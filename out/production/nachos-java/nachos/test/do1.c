#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

#define TICKS   10
#define LOOP    10


int main(int argc, char** argv)
{
    int i=0;
    for(i=0;i<LOOP;i++){
        printf("2 \n");
        sleep(TICKS);
    }
    return 0;
}
