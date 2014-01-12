Overview
--------

This is a Pascal compiler written in Java. It's rewritten from a C version I wrote during a compilers class (which I won't put on the internet). The compiler implements a portion of Pascal (see below). It is known to compile a few trivial programs successfully (these are included).

### Building/Running ###

To compile a program, you'll need both [NASM](http://www.nasm.us/) and a C compiler like GCC or Clang installed. The compiler generates x86 Assembly (NASM syntax), and it uses a small library of math and printing functions (`pascal_lib.c`).

Once Java, NASM, and GCC are installed, use the makefile to compile and run the included programs. To compile and run `trivb.pas`, execute `make trivb`:

    $ make trivb
    javac src/compiler/*.java src/compiler/assembly/*.java
    Note: Some input files use unchecked or unsafe operations.
    Note: Recompile with -Xlint:unchecked for details.
    cd src && java compiler.CodeGenRun ../trivb.pas > ../trivb.asm
    nasm -f win32 -o trivb.o trivb.asm
    gcc -o trivb trivb.o pascal_lib.c
    ...

This is a four-step process, unfortunately:
  
  1. Build the compiler: `javac src/compiler/*.java src/compiler/assembly/*.java`
  2. Generate assembly: `cd src && java compiler.CodeGenRun ../trivb.pas > ../trivb.asm`
  3. Assemble: `nasm -f win32 -o trivb.o trivb.asm`
  4. Link everything and produce an executable: `gcc -o trivb trivb.o pascal_lib.c`

### Problems/Features ###

Language support:
  
  * `integer` is 32-bit int. `+`, `-`, `*` operators work with integers
  * `real` is a 32-bit float. `+`, `-`, `*`, `/` are implemented for reals
  * pointers (32-bits), enums, arrays, and records are implemented
  * boolean operators: `<`, `<=`, `>`, `>=`, `=`, `<>`
  * `if`, `for`, `while`, `repeat...until` constructs
  * `const`, `var`, and `type` blocks
  * the `label` and `goto` keywords
  * `write`, `writeln`, and various math functions are built-in

Notable features missing or untested:

  * division (for integers), modulus, bitwise operations
  * procedures (user-defined functions)
  * the `case` and `with` keywords

 
