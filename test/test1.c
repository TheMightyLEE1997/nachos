/* test1.c
 *	Simple program to do some computation and make a function call, test whether the transformation between virtual memory and physical memory is correct.
 */

#include "stdio.h"
#include "syscall.h"

int main() {
    int a = 1;
    int b = 2;
    int c = add(a, b);
    printf("The sum of %d and %d is %d.\n", &a, &b, &c);
    return 0;
}

int add(int a, int b) {
    return a+b;
}
