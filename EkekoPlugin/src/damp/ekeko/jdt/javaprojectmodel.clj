(ns 
 ^{:doc "Central point of access to JavaProjectModel instances managed by the EkekoModel for reification relations."
    :author "Coen De Roover"}
  damp.ekeko.jdt.javaprojectmodel
  (:require [damp.ekeko [ekekomodel :as ekekomodel]])
  (:import 
    [damp.ekeko JavaProjectModel]
    [org.eclipse.core.resources IProject]
    [org.eclipse.jdt.core JavaCore IJavaProject IPackageFragmentRoot IPackageFragment IMember IType ITypeHierarchy]
    [org.eclipse.jdt.core.dom CompilationUnit IBinding TypeDeclaration MethodDeclaration IMethodBinding 
     AnonymousClassDeclaration MethodInvocation SuperMethodInvocation ClassInstanceCreation ConstructorInvocation
     SuperConstructorInvocation]))


;; Ekeko Java Projects
;; -------------------

(defn 
  java-project-models
  "Returns all JavaProjectModel instances that are to be queried.
   Subset of (queried-project-models), which is itself 
   a subset of (all-project-models)."
  []
  (filter (fn [project-model] (instance? JavaProjectModel project-model))
          (ekekomodel/queried-project-models)))


(defn 
  ekeko-javaprojects
  "Returns all IJavaProject instances that have the Ekeko nature enabled."
  []
  (map (fn [model] (.getJavaProject model))
       (java-project-models)))

; IJavaProject
; ------------

(defn 
  project-as-javaproject
  "Returns the IJavaProject for given IProject."
  [^IProject project]
  (when (.isNatureEnabled project (JavaCore/NATURE_ID))
    (JavaCore/create project)))


(defn 
  javaproject-name
  "Returns the name of the given IJavaProject."
  [^IJavaProject project]
  (.getName ^IProject (.getProject project)))

;Packages within an IJavaProject
;-------------------------------

(defn 
  javaproject-packagefragmentroots
  "Returns the package fragment roots for the given IJavaProject."
  [^IJavaProject project]
  (.getAllPackageFragmentRoots project))

(defn 
  packagefragmentroot-binary?
  "Succeeds for package fragment roots that originate from a binary."
  [^IPackageFragmentRoot pr]
  (= IPackageFragmentRoot/K_BINARY (.getKind pr)))

(defn 
  packagefragmentroot-source?
  "Succeeds for package fragment roots that originate from source."
  [^IPackageFragmentRoot pr]
  (= IPackageFragmentRoot/K_SOURCE (.getKind pr)))

(defn 
  packagefragmentroot-fragments
  "Returns the package fragments in the given package fragment root."
  [^IPackageFragmentRoot pr]
  (.getChildren pr))

(defn
  packagefragmentroot-has-subpackages?
  "Succeeds for package fragments that have subpackages."
  [^IPackageFragment p]
  (.hasSubpackages p))

(defn packagefragment-name
  "Returns the name of the give package fragment."
  [^IPackageFragment p]
  (.getElementName p))


;; Mappings between IBinding / IJavaElement / ASTNode 
;; --------------------------------------------------

(defn 
  icu-to-ast
  "Returns the CompilationUnit ASTNode (i.e., the complete abstract syntax tree)
   for the given ICompilationUnit instance (i.e., a IJavaElement),
   provided ICompilationUnit resides in JavaProject managed by the EkekoModel."
  [icu]
  (.getCompilationUnit
    (.getJavaProjectModel (ekekomodel/ekeko-model) (.getJavaProject icu))
    icu))

(defn 
  declaration-to-ielement 
  "Returns the IJavaElement corresponding to the given ASTNode."
  [t]
  (-> t .resolveBinding .getJavaElement))

(defn 
  ielement-to-declaration 
  "Returns the ASTNode that declares the given IJavaElement."
  [^IMember ije] 
  (when-let [icu (.getCompilationUnit ije)] ;nil if itype came not from source
    (.findDeclaringNode
      ^CompilationUnit (icu-to-ast icu)
      ^String (.getKey ije))))

(defn 
  binding-to-declaration
  "Returns the ASTNode that declares the given IBinding."
  [^IBinding b]
  (when-let [ije (.getJavaElement b)]
    (ielement-to-declaration ije)))



; JDT Type Hierachy
; -----------------

(defn
  itype-type-hierarchy
  [^IType it]
  ;(.newTypeHierarchy it nil))
  (.getTypeHierarchy (ekekomodel/ekeko-model) it))
  
(defn 
  ^ITypeHierarchy 
   type-declaration-type-hierarchy
  [t]
  (-> t declaration-to-ielement itype-type-hierarchy))

(defn type-declaration-subtypes-itypes [^TypeDeclaration t]
  (let [h (type-declaration-type-hierarchy t)]
    (.getSubtypes h (.getType h))))

(defn type-declaration-subclasses-itypes [^TypeDeclaration t]
  (let [h (type-declaration-type-hierarchy t)]
    (.getSubclasses h (.getType h))))

    
; JDT Call Graph
; --------------

(defn method-overriders
;  (memoize 
;    (fn  
      [^MethodDeclaration m]
  (let [mbinding (.resolveBinding m)]
    (mapcat
      ;(apply concat (pmap
      (fn [itype] 
              (if-let [subclass (ielement-to-declaration itype)]
                (filter 
                  (fn [d] 
                    (and (instance? MethodDeclaration d)
                         (.overrides ^IMethodBinding (.resolveBinding ^MethodDeclaration d)  mbinding)))
                  (if
                    (instance? AnonymousClassDeclaration subclass)
                    (.bodyDeclarations ^AnonymousClassDeclaration subclass)
                    (.getMethods ^TypeDeclaration subclass)))
                []))
            (type-declaration-subclasses-itypes (.getParent m)))))
      
   
(defn targets-of-constructor-invocation [n]
  (if-let [cb (.resolveConstructorBinding n)]
   (if-let [m (binding-to-declaration cb)]
     [m]
     [])
   []))

(defn targets-of-invocation [i]
  (if-let [mb (.resolveMethodBinding i)]
    (if-let [static-target (binding-to-declaration mb)]
      (conj  (method-overriders static-target) static-target)
      [])
    []))

(defmulti invocation-targets class)
(defmethod invocation-targets MethodInvocation [i]
  (targets-of-invocation i))
(defmethod invocation-targets SuperMethodInvocation [i]
  (targets-of-invocation i))
(defmethod invocation-targets ClassInstanceCreation [n]
  (targets-of-constructor-invocation n))
(defmethod invocation-targets ConstructorInvocation [i]
  (targets-of-constructor-invocation i))
(defmethod invocation-targets SuperConstructorInvocation [i]
  (targets-of-constructor-invocation i))




 
