.class public kilim/test/ex/ExJSR
.super java/lang/Object

.method public <init>()V
   aload_0
 
   invokenonvirtual java/lang/Object/<init>()V
   return
.end method


;; --------------------------------------------
;; Make a single jsr call that simply returns
;; --------------------------------------------
.method private static simpleJSR()V
    .limit locals 3
    .limit stack 3

    bipush 100
    istore 1
    jsr D
    iload 1
    istore 0
    return

    D:
    astore 2
    ret 2
    
.end method

;; --------------------------------------------
;; Single jsr call that calls pausable method
;; The number of basic blocks should be 4
;; --------------------------------------------
.method private static pausableJSR1()V
    .throws kilim/Pausable
    .limit locals 3
    .limit stack 3
;; BB 0
    bipush 100
    istore 1
    jsr D
;; BB 1
    iload 1
    istore 0
    return

    D:
;; BB 2
    astore 2
;; BB 3
    invokestatic kilim/test/ex/ExBasicBlock/pausable()V
    ret 2
    
.end method


;; --------------------------------------------
;; Multiple jsr calls to a pausable subr
;; The number of basic blocks should be 7
;; because the number of basic blocks without
;; inlining is 5, and inlining the second
;; jsr adds another two. 
;; --------------------------------------------
.method private static pausableJSR2()V
    .throws kilim/Pausable
    .limit locals 3
    .limit stack 3
  
    bipush 100
    istore 1
    jsr D
 
    jsr D
 
    iload 1
    istore 0
    return
  D:
    astore 2
    invokestatic kilim/test/ex/ExBasicBlock/pausable()V
    ret 2
    
.end method

