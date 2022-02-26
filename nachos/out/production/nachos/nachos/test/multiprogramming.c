#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

#define TICKS   10
#define LOOP    5

int main(int argc, char** argv)
{
    int pid = exec("do1.coff", argc, argv);
    int i=0;
    for(i=0;i<LOOP;i++){
        printf("1 \n");
        sleep(TICKS);
    }
    int status;
    join(pid, &status);
    return 0;
}
