#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int mystrlen(char *buffer)
{
	int i;
	for(i=0;i<500;i++)
	{
		if(buffer[i]==0)
			return i;
	}
}

void main()
{
	int fd=0;
	char *filename="aa.txt";
	int ByteNum;
	char *buffer="Hello! This is the test for Task2.1\n";
	char buffersize=mystrlen(buffer);
	char buf[40];
	
	creat(filename);
	printf("Calling 'creat(filename)'...");
	printf("	done!\n");
	fd=open(filename);
	printf("Calling 'fd=open(filename)'...done!\n");
	printf("return value fd =\n");
	
	write(fd,buffer,buffersize);
	close(fd);
	printf("Calling 'write'...done!\n");
	
	fd=open(filename);
	int i;
	ByteNum=read(fd,buf,40);
	printf("Calling read()...done!\n");
	printf(buf);
	
	close(fd);
	halt();
}
