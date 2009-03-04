.class public synchronized kilim/test/ex/ExYieldSub
.super kilim/test/ex/ExYieldBase

; -------------------------------------------------------------
.method public <init>()V
; -------------------------------------------------------------

    aload 0
    invokespecial kilim/test/ex/ExYieldBase/<init>()V
    return
    .limit stack 1
    .limit locals 1
.end method

; -------------------------------------------------------------
.method public execute()V
; -------------------------------------------------------------
    .throws kilim/Pausable

    aload 0
    iconst_0
    putfield kilim/test/ex/ExYieldSub/doPause Z
    aload 0
    invokespecial kilim/test/ex/ExYieldSub/test()V
    aload 0
    iconst_1
    putfield kilim/test/ex/ExYieldSub/doPause Z
    aload 0
    invokespecial kilim/test/ex/ExYieldSub/test()V
    return
    .limit stack 2
    .limit locals 1
.end method

; -------------------------------------------------------------
.method private test()V
; -------------------------------------------------------------
    .throws kilim/Pausable

    aload 0
    getfield kilim/test/ex/ExYieldSub/testCase I
    tableswitch  0
        L0
        L1
        L2
        L3
        default: L0
  L0:
    getstatic kilim/test/ex/ExYieldSub/fd D
    getstatic kilim/test/ex/ExYieldSub/ff F
    aload 0
    getfield kilim/test/ex/ExYieldSub/doPause Z
    invokestatic kilim/test/ex/ExYieldSub/nonPausableJSRs(DFZ)V    
    goto L4

  L1:
    getstatic kilim/test/ex/ExYieldSub/fd D
    getstatic kilim/test/ex/ExYieldSub/ff F
    aload 0
    getfield kilim/test/ex/ExYieldSub/doPause Z
    invokestatic kilim/test/ex/ExYieldSub/singlePausable(DFZ)V
    goto L4

  L2:
    getstatic kilim/test/ex/ExYieldSub/fc C
    getstatic kilim/test/ex/ExYieldSub/fl J
    aload 0
    getfield kilim/test/ex/ExYieldSub/doPause Z
    invokestatic kilim/test/ex/ExYieldSub/multiplePausable(CJZ)V
    goto L4

  L3:
    getstatic kilim/test/ex/ExYieldBase/fd D
    getstatic kilim/test/ex/ExYieldBase/fs Ljava/lang/String;
    getstatic kilim/test/ex/ExYieldBase/fl J
    aload 0
    getfield kilim/test/ex/ExYieldSub/doPause Z
    invokestatic kilim/test/ex/ExYieldSub/mixedPausable(DLjava/lang/Object;JZ)V

  L4:
    return
    .limit stack 10
    .limit locals 1
.end method

; -------------------------------------------------------------
.method public static nonPausableJSRs(DFZ)V
; nonPausableJSRs(double d, float f, boolean doPause)
; -------------------------------------------------------------
    ; load stack before jsr. This gets consumed by SUB2 when SUB 1 calls it.

    dload 0
    fload 2
    jsr L1

    return

  L1:
    astore 4
    dload 0
    invokestatic kilim/test/ex/ExYieldBase/verify(D)V
    fload 2
    invokestatic kilim/test/ex/ExYieldBase/verify(F)V
    jsr L2
    ret 4

  L2:
    astore 5
    invokestatic kilim/test/ex/ExYieldBase/verify(F)V
    invokestatic kilim/test/ex/ExYieldBase/verify(D)V
    ret 5
    
    .limit stack 10
    .limit locals 6
.end method

    
; -------------------------------------------------------------
.method public static singlePausable(DFZ)V
; single(double d, float f, boolean doPause)
; -------------------------------------------------------------
    .throws kilim/Pausable

    ; load stack before JSR
    dload 0
    fload 2
    jsr L
    
    ; make sure local vars haven't been tampered with
    dload 0
    invokestatic kilim/test/ex/ExYieldBase/verify(D)V
    fload 2
    invokestatic kilim/test/ex/ExYieldBase/verify(F)V
    return

   L:
    astore 4                                              ; ret address
    iload 3
    ifeq L1                                               ; if doPause
    ldc2_w 10
    invokestatic kilim/Task/sleep(J)V                     ;    Task.sleep(10)
   L1:
    ; verify stack is preserved
    invokestatic kilim/test/ex/ExYieldBase/verify(F)V
    invokestatic kilim/test/ex/ExYieldBase/verify(D)V

    ; verify local vars are preserved
    dload 0
    invokestatic kilim/test/ex/ExYieldBase/verify(D)V
    fload 2
    invokestatic kilim/test/ex/ExYieldBase/verify(F)V

    ret 4
    .limit stack 10
    .limit locals 5
.end method

; -------------------------------------------------------------
.method public static multiplePausable(CJZ)V
; -------------------------------------------------------------
    .throws kilim/Pausable

    ; load stack before JSR
    iload 0
    lload 1
    jsr SUB1

    lload 1
    iload 0
    jsr SUB2

    iload 0
    lload 1
    jsr SUB1

    ; make sure local vars haven't been tampered with
    iload 0
    invokestatic kilim/test/ex/ExYieldBase/verify(I)V
    lload 1
    invokestatic kilim/test/ex/ExYieldBase/verify(J)V
    return

  ; SUB1 consumes long then int on stack, and leaves it empty
  SUB1:
    astore 4                                              ; ret address
    iload 3
    ifeq L1                                               ; if doPause
    ldc2_w 10
    invokestatic kilim/Task/sleep(J)V                     ;    Task.sleep(10)

  L1:
    ; verify stack is preserved
    invokestatic kilim/test/ex/ExYieldBase/verify(J)V
    invokestatic kilim/test/ex/ExYieldBase/verify(I)V

    ; verify local vars are preserved
    iload 0
    invokestatic kilim/test/ex/ExYieldBase/verify(I)V
    lload 1
    invokestatic kilim/test/ex/ExYieldBase/verify(J)V
    ret 4

  ; SUB2 consumes int then long on stack, and leaves it empty
  SUB2:
    astore 4                                              ; ret address
    iload 3
    ifeq L2                                               ; if doPause
    ldc2_w 10
    invokestatic kilim/Task/sleep(J)V                     ;    Task.sleep(10)

  L2:
    ; verify stack is preserved
    invokestatic kilim/test/ex/ExYieldBase/verify(I)V
    invokestatic kilim/test/ex/ExYieldBase/verify(J)V

    ; verify local vars are preserved
    iload 0
    invokestatic kilim/test/ex/ExYieldBase/verify(I)V
    lload 1
    invokestatic kilim/test/ex/ExYieldBase/verify(J)V
    ret 4

    .limit stack 10
    .limit locals 5
.end method


; -------------------------------------------------------------
.method public static mixedPausable(DLjava/lang/Object;JZ)V
; -------------------------------------------------------------
    .throws kilim/Pausable

    ; throw in some constants in the stack
    sipush 10
    bipush 10
    ldc 10.0
    ldc2_w 10.0
    ldc2_w 10
    ldc "10"
    ; some duplicates
    dup
    dload 0
    ; stack has S B F D J Str Str D
    ; The subroutines below don't touch the stack, so we'll verify 
    ; if they have been preserved at the end.

    jsr NonPausableSub

    jsr PausableSub

    jsr NonPausableSub

    jsr PausableSub

    ; make sure local vars haven't been tampered with
    dload 0
    invokestatic kilim/test/ex/ExYieldBase/verify(D)V
    aload 2
    checkcast java/lang/String
    invokestatic kilim/test/ex/ExYieldBase/verify(Ljava/lang/String;)V

    ; make sure all stack vars are preserved.
    invokestatic kilim/test/ex/ExYieldBase/verify(D)V
    invokestatic kilim/test/ex/ExYieldBase/verify(Ljava/lang/String;)V
    invokestatic kilim/test/ex/ExYieldBase/verify(Ljava/lang/String;)V
    invokestatic kilim/test/ex/ExYieldBase/verify(J)V    
    invokestatic kilim/test/ex/ExYieldBase/verify(D)V
    invokestatic kilim/test/ex/ExYieldBase/verify(F)V
    invokestatic kilim/test/ex/ExYieldBase/verify(I)V
    invokestatic kilim/test/ex/ExYieldBase/verify(S)V

    return

  NonPausableSub:
    ; just to test that if the entry and exit are in the same BB
    ; it is ok.
    astore 6  
    ret 6

  PausableSub:
    astore 6                                              ; ret address
    iload 5
    ifeq NOSLEEP                                          ; if doPause
    ldc2_w 10
    invokestatic kilim/Task/sleep(J)V                     ;    Task.sleep(10)

  NOSLEEP:
    ; verify sample local vars are preserved
    lload 3
    invokestatic kilim/test/ex/ExYieldBase/verify(J)V
    dload 0
    invokestatic kilim/test/ex/ExYieldBase/verify(D)V
    ret 6

    .limit stack 20
    .limit locals 7
.end method


