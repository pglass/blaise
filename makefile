
all: triv trivb graph1 pasrec

classfiles:
	javac src/compiler/*.java src/compiler/assembly/*.java

triv.asm: classfiles
	cd src && java compiler.CodeGenRun ../triv.pas > ../triv.asm

trivb.asm: classfiles
	cd src && java compiler.CodeGenRun ../trivb.pas > ../trivb.asm

graph1.asm: classfiles
	cd src && java compiler.CodeGenRun ../graph1.pas > ../graph1.asm

pasrec.asm: classfiles
	cd src && java compiler.CodeGenRun ../pasrec.pas > ../pasrec.asm

triv: triv.asm
	nasm -f win32 -o triv.o triv.asm
	gcc -o triv triv.o pascal_lib.c
	./triv

trivb: trivb.asm
	nasm -f win32 -o trivb.o trivb.asm
	gcc -o trivb trivb.o pascal_lib.c
	./trivb

graph1: graph1.asm
	nasm -f win32 -o graph1.o graph1.asm
	gcc -o graph1 graph1.o pascal_lib.c
	./graph1

pasrec: pasrec.asm
	nasm -f win32 -o pasrec.o pasrec.asm
	gcc -o pasrec pasrec.o pascal_lib.c
	./pasrec
	
clean:
	rm -f src/compiler/*.class
	rm -f *.run
	rm -f *.o
	rm -f *.asm
	rm -f *.exe
