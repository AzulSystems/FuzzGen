" Vim syntax file
" " Language: FuzzGen Grammar Files
" " Maintainer: Nina Rinskaya
" " Latest Revision: 10 Aug 2021
"

syn clear

" Keywords

syn region fuzzgenRuleBlock matchgroup=fuzzgenBlock start="BEGIN_RULE" end="END_RULE" keepend fold transparent contains=ALL
syn region fuzzgenScope matchgroup=fuzzgenBlock start="BEGIN_SCOPE" end="END_SCOPE" keepend fold transparent contains=ALL
" syn region fuzzgenRuleBlock start="BEGIN_RULE" end="BEGIN_RULE" fold transparent contains=fuzzgenNonTerminal,fuzzgenTerminalString,fuzzgenRuleBlock,fuzzgenScope
" syn region fuzzgenScope start="BEGIN_SCOPE" end="END_SCOPE" fold transparent contained

syn match fuzzgenComment "//.*$"
syn match fuzzgenNewLine "\\n"

syn match fuzzgenCommand "#CREATE_[^[:space:]]*"
syn match fuzzgenCommand "#REUSE_[^[:space:]]*"
syn match fuzzgenCommand "#GET_[^[:space:]]*"

syn match fuzzgenNonTerminal "@[^[:space:]]*"

"syn match fuzzgenEnvVarName "[^[:space:]]*" nextgroup=fuzzgenEnvVarSetOp contained
"syn match fuzzgenEnvVarValue "[^[:space:]]*"  nextgroup=fuzzgenEnvVarValue
"syn match fuzzgenEnvVarSetOp "=" nextgroup=fuzzgenEnvVarValue
" syn region fuzzgenSet start="SET" end="=" keepend 

syn keyword fuzzgenInclude INCLUDE
syn keyword fuzzgenSet SET
syn keyword fuzzgenRegisterLazyIDs REGISTER_LAZY_IDS 

" syn region fuzzgenTerminalString start='`' end='`' contained
syn region fuzzgenTerminalString start='`' end='`' 

hi def link fuzzgenInclude       Include
hi def link fuzzgenComment     Comment
hi def link fuzzgenRuleBlock    Repeat
hi def link fuzzgenScope    Repeat
hi def link fuzzgenNonTerminal      Identifier
hi def link fuzzgenTerminalString      String
hi def link celDesc        PreProc
hi def link celNumber      Constant
hi def link  fuzzgenSet Statement
hi def link  fuzzgenBlock Type
hi def link fuzzgenEnvVarSetOp  Operator
hi def link fuzzgenRegisterLazyIDs Statement
hi def link fuzzgenCommand Statement
hi def link fuzzgenNewLine Special


if exists("b:fuzzgen")
   finish
endif

let b:current_syntax = "fuzzgen"


