# FuzzGen

FuzzGen - is an automated test generator, which uses fuzzing technique to generate tests for C++, Java, Scala and LLVM IR.
Fuzzing process is based on the specified grammar of the test. 

# Comment to this manual

In the following description these rules will apply: 
* `<val>` - parameter of the described construct ("`val`" can be changed to any other string, this string is considered this parameter name).
  * if parameter name is preceded with `otp_` then it is optional. 
  
# Grammar

Grammar consists of [non-terminals](#non-terminals), forward-declarations, environmental variables and other included grammars.  
Grammar file has .fg extension.  
All grammar files for language `<lang>` are located in `configs_<lang>` directory.  
When grammar includes another grammar, the including grammar contains all non-terminals, forward declarations, environmental variables and included grammars of the included grammar.   
Grammars are parameterized, which means that they can contain parameter templates, which are to be replaced, when this grammar is included in another grammar.  

# Non-terminals

Non-terminal is defined if it has at least one rule associated with it.    
If no rule description follows non-terminal declaration then this non-terminal is considered forward-declared and expected to be defined later.  
Stage - is an attribute of non-terminal. If n > m than non-terminals of stage m will be processed before non-terminals of stage n.    
Appendable non-terminal - non-terminal which can have its rules defined in different parts of grammar.  
Concat non-terminal - non-terminal, which only rule lexemes can be declared in multiple places in grammar.  

Non-terminals are declared using the following syntax:
* `@<NON_TERMINAL_NAME><opt_stage>` - these construct describes non-terminal named `<NON_TERMINAL_NAME>` with stage `<opt_stage>`.
  * `<opt_stage>` is an integral expression enclosed in "`[]`". If it is not defined then stage is considered 0.  

Parameterized non-terminal can contain parameter template `$<N>`, where `<N>` is the number of parameter of the enclosing grammar.

# #Commands

* `SET <env>=<val>` - set value of environmental variable `<env>` to `<val>` (environmental variable is global if it innermost scope is global scope, otherwise its local).
* `BEGIN_SCOPE` - start new scope.
* `END_SCOPE` - end scope started previously with `#BEGIN_SCOPE`.
* `BEGIN_RULE`/`APPEND_RULE`/`CONCAT_RULE` `: <weight>` - indicates rule description start. Every lexeme between this command and `#END_RULE` will be considered part of this rule.
  * `<weight>` - weight of the rule, which is considered in processing of non-terminal with more than one rule. The bigger the weight - the higher the probability the rule will be chosen.  
* `END_RULE` - indicates rule description end. 
  * Non-terminal, which has rule starting with `#BEGIN_RULE` can have one or more rules and all these rules must start with `#BEGIN_RULE` and form a sequence with no intermediate lexemes, which are not part of these rules. That non-terminal will be **non-appendable** and **non-concat**.
  * Non-terminal, which has rule starting with `#APPEND_RULE` can have one or more rules and all these rules must start with `#APPEND_RULE`. These rules can be located in different parts of grammar. That non-terminal will be **appendable** and **non-concat**.
  * Non-terminal, which has rule starting with `#CONCAT_RULE` can only have one rule, lexemes of that rule can be specified in more one place. All these lexemes should be preceded with `#CONCAT_RULE`. That non-terminal will be **non-appendable** and **concat**.
* `INCLUDE <filename> <opt_params>` - include grammar stored in filename parameterized with opt_params.
  * `<opt_params>` looks like `(<param1_1> <param1_2> ... <param1_N1>, ..., <paramM_1> ... <paramM_NM>)`. For each combination of `param1_i1, ..., paramsM_iM` there will be included grammar. That means that overall there will be N1 * ... * NM grammars included.
* `CREATE_ID` - create unique ID. Can be used to create unique method names.

# Common non-terminals

* `MAIN` - first processed non-terminal.

# Java non-terminals 

* [`MAIN`](configs_java/mainGrammar.fg)
* [`PACKAGE_DECLARATION`](configs_java/settingsShortBasic.fg) - package declaration (absent at this time)
* [`IMPORTS`](configs_java/mainGrammar.fg) - import statement sequence, which consist of:
  * [`IMPORTS_MAIN`](configs_java/settingsShortBasic.fg) - import statement sequence for class from Java Standard Libraries.  
  * [`IMPORTS_ADDITIONAL`](configs_java/settingsShortBasic.fg) - "additional" import statement sequence (absent at this time).
* [`UTILS_CLASS`](configs_java/mainGrammar.fg) - fuzzing utility class, which can be used in test classes. 
  * [`UTILS_CLASS_NAME`](configs_java/mainGrammar.fg) - fuzzing utility class name.
  * [`UTILS_FIELDS`](configs_java/utils.fg) - fields of fuzzing utility class name.
  * [`UTILS_METHODS_COMMON`](configs_java/utils.fg) - fuzzing class methods:
    * [`UTILS_INIT_ARRAY_$1`](configs_java/utilsByType.fg) - static method that performs initialization of on array of type `$1` using seed of that type supplied as parameter.
  * [`DECLARE_CHECKSUM_COLLECTION`](configs_java/mainGrammar.fg) - fuzzing class methods:
    * [`DECLARE_CHECKSUM_COLLECTION_VARS_METHOD`]() - static method which counts sum of two-dimensional array of given type. 
    * [`DECLARE_CHECKSUM_LIST_VARS_METHOD`](configs_java/checksumListMethods.fg) - static method which counts sum of two-dimensional list. 
      * [`CHECKSUM_LIST_VARS_METHOD_BODY_$1`](configs_java/checksumListMethods.fg) - body of that method.
        * [`CHECKSUM_LIST_VARS_METHOD_SUM_$1`](configs_java/checksumListMethods.fg) - statement of that body, which directly describes summation.
  * [`DECLARE_PRINT_COLLECTION_VARS_METHOD`](configs_java/mainGrammar.fg) - static methods for printing elements of arrays of primitive types and their wrappers.
    * [`DECLARE_PRINT_COLLECTION_VARS_METHOD_$1`](configs_scala/printArrayMethods.fg) - static method for printing elements of array of type `$1`.
      * [`PRINT_COLLECTION_VARS_METHOD_BODY_$1`](configs_scala/printArrayMethods.fg) - body of that method.
* [`ANOTHER_CLASS`](configs_java/synchronizedBlock.fg) - class with one int field and one method.
  * [`ANOTHER_CLASS_NAME`](configs_java/synchronizedBlock.fg) - that class name.
* [`LAMBDA_INTERFACES`](configs_java/mainGrammar.fg) - non-terminal, which can be expanded to any arbitrary number of lambda interfaces.
  * [`LAMBDA_INTERFACE`](configs_java/lambdaByType.fg): 
    * [`LAMBDA_INTERFACE_ONE_ARG_$1`](configs_java/lambdaByType.fg) - lambda-interface with one argument of type `$1`.
    * [`LAMBDA_INTERFACE_ONE_ARG_$2`](configs_java/lambdaByType.fg) - lambda-interface with two arguments of type `$1`.
* [`SUPER_CLASS_A`](configs_java/mainGrammar.fg) - some superclass A. 
* [`SUPER_CLASS_B`](configs_java/mainGrammar.fg) - some superclass B.
* [`CHILD_CLASSES`](configs_java/mainGrammar.fg) - non-terminal, which can be extended to any arbitrary number of child classes, represented by non-terminal `@OTHER_CLASS`.
* [`OTHER_CLASS`](configs_java/mainGrammar.fg) - some class (will be referred to as "other class").
  * [`OTHER_CLASS_COMMENT`](configs_java/mainGrammar.fg) - other class comment.
  * [`OTHER_CLASS_NAME_DECLARE`](configs_java/mainGrammar.fg) - other class name.
  * [`MAYBE_EXTENDS_SUPER_CLASS`](configs_java/mainGrammar.fg) - non-terminal, which can be extended to `extends <SUPER_CLASS_A>` or `extends <SUPER_CLASS_B>` or none.
  * [`TEST_CLASS_FIELDS`](configs_java/mainGrammar.fg) - non-terminal, which can be extended to any arbitrary number of field not lesser than one. 
  * [`CONSTRUCTOR_METHOD`](configs_java/mainGrammar.fg) - constructor method of the class.
* [`SET_GLOBAL_COUNTERS_TO_ZERO`](configs_java/parameters.fg) - assign 0 to all global counters (environmental variables).
  * [`SET_GLOBAL_COUNTERS_TO_ZERO_$1_$2`](configs_cpp/parametersByType.fg) - assign 0 to all global (`$2` == var ? : im)mutable counters of type `$1`.
* [`SET_DECLARED_FLAG_TO_ZERO`](configs_java/parameters.fg) - assign 0 to all declared-flags.
* [`SET_DECLARED_FIELDS_FLAG_TO_ZERO`](configs_java/parameters.fg) - assign 0 to all field and array field declaration counters.
  * [`SET_DECLARED_FIELDS_FLAG_TO_ZERO_$1_$2`](configs_cpp/parametersByType.fg) - assign 0 to (`$2` == var ? : im)mutable field and array field declaration of type `$1` counter.
* [`RETURN_TYPE_$1`](configs_java/checksumArrayMethods.fg) - type of the return value, depends on `$1IsFloatingPointType` and `$1IsStringType` environmental variables (can be `double` or `long` at this time).
