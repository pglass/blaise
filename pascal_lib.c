#include <stdio.h>
#include <math.h>
#include <stdlib.h>

#define FUNC32(a) a##32(float f) { return a((double) f); }

#if defined(__GNUC__)
#  define PRE_CDECL
#  define POST_CDECL __attribute__((cdecl))
#else
#  define PRE_CDECL __cdecl
#  define POST_CDECL
#endif

int PRE_CDECL asm_main( void ) POST_CDECL;

int main() {
	return asm_main();
}

void write(char str[]) { printf("%s", str); }

void writeln(char str[]) { printf("%s\n", str); }

void writef(double x) { printf("%g", x); }

void writei(int n) { printf("%d", n); }

void writelnf(double x) { printf("%g\n", x); }

void writelni(int n) { printf("%d\n", n); }

int* new(int size) { return (int*) malloc(size); }

int iround(double x) {  
	int n;
	if ( x >= 0.0 )
		n = x + 0.5;
	else n = x - 0.5;
	return ( n );
}

float FUNC32(sin)
float FUNC32(cos)
float FUNC32(sqrt)
float FUNC32(exp)
float FUNC32(round)
float FUNC32(iround)
void FUNC32(writef)
void FUNC32(writelnf)
