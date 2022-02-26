package nachos.network; ///NEW/////

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.OpenFile;
import nachos.machine.Packet;
import nachos.userprog.UserProcess;
import nachos.vm.VMProcess;

/**
 * A <tt>VMProcess</tt> that supports networking syscalls.
 */
public class NetProcess extends VMProcess {
    /**
     * Allocate a new process.
     */
    private static final int RETRANSMIT_INTERVAL = 20000;
    private final int MAX_SOCKETS = 16;
    private Socket[] socketList;
    public NetProcess() {
        super();
        socketList = new Socket[MAX_SOCKETS];
    }
    protected int getAvailIndex() {
        for(int i = 2; i < MAX_SOCKETS; i++)
            if(socketList[i] == null)
                return i;
        return -1;
    }
    /**
     * *这是连接功能。通常是客户端发送
     * *它接受主机IP地址和要连接的端口。
     * *此函数发送一个syn包，并等待服务器用synack应答。
     * *一旦它得到synack，它就会用ack进行应答
     *
     * @param host
     * @param port
     * @return
     */
    private int handleConnect(int host, int port) {
        int srcLink = Machine.networkLink().getLinkAddress();
//        if(NetKernel.postOffice.availPorts.isEmpty())
//        {
//            return -1;
//        }
//        int srcPort = NetKernel.postOffice.availPorts.first();
        int srcPort = NetKernel.postOffice.getUnusedPort();
//        NetKernel.postOffice.availPorts.remove(NetKernel.postOffice.availPorts.first());
        int res;
        Socket socket = null;
        
        //检查是否存在相同的 文件描述符
        if ((res = checkExistingConnection(host, srcLink, port, port)) == -1) {
            //如果不存在则新建
            socket = new Socket(host, port, srcLink, srcPort);
            int i = findEmpty();
            openfile[i] = new OpenFile(null,"connect");
            socketList[i] = socket;
            //openfile[i].setFileName("connect");
            res = i;
        }

         //如果存在寻找之前旧的文件描述符
        if (socket == null) socket = (Socket) socketList[res];
        srcPort = socket.sourcePort;

        try {
            //SYN表示此数据包是启动后的第一个数据包
            UdpPacket packet = new UdpPacket(host, port, srcLink, srcPort, UdpPacket.SYN, 0, new byte[0]);
            //发送packet
            NetKernel.postOffice.send(packet);

            System.out.println("SYN包已发送，挂起等待回复");

            /**
             * 当用户进程调用connect（）系统调用时，活动端点发送同步数据包（syn）。
             * 这将导致创建挂起的连接。在被动端点的用户进程调用accept（）系统调用之前，
             * 此挂起连接的状态必须存储在接收器上。调用accept（）时，
             * 被动端向主动端发送一个syn确认数据包（syn/ack），并建立连接。
             */
            
            UdpPacket SynAckPack = NetKernel.postOffice.receive(srcPort);
            
            while (SynAckPack == null)
            {
                NetKernel.alarm.waitUntil(RETRANSMIT_INTERVAL);
                SynAckPack = NetKernel.postOffice.receive(srcPort);
                if(SynAckPack != null)
                {
                    break;
                }
            }
            System.out.println("服务器已收到SYN包");
            //收到确认的数据包
            if (SynAckPack.status == UdpPacket.SYNACK && Machine.networkLink().getLinkAddress() == SynAckPack.packet.dstLink) {
                System.out.print("SYNACK已经收到: ");
                System.out.println(SynAckPack);
                //发回ack。
                UdpPacket ackPack = new UdpPacket(host, port, srcLink, srcPort, UdpPacket.ACK, SynAckPack.seqNum + 1, new byte[0]);
                NetKernel.postOffice.send(ackPack);
                //确认发送此时可以发送数据。 连接已经建立
            }


        } catch (MalformedPacketException e) {
            return -1;
        }
        return res;
    }
    /**
     *
     * @param port
     * @return
     */
    private int handleAccept(int port) {
     Lib.assertTrue(port >= 0 && port < Packet.linkAddressLimit);
     
        UdpPacket mail = NetKernel.postOffice.receive(port);
        while (mail == null)
        {
            NetKernel.alarm.waitUntil(RETRANSMIT_INTERVAL);
            mail = NetKernel.postOffice.receive(port);
            if(mail != null)
            {
                break;
            }
        }
        if (mail == null) {
        	
            return -1;
        }
        //添加端口信息  已经包的序列号
        
        int srcPort = mail.destPort;
        int sourceLink = mail.packet.dstLink;
        int destinationLink = mail.packet.srcLink;
        int destinationPort = mail.srcPort;
        int seq = mail.seqNum + 1;
        int res;
        //确认文件描述符还不存在
        if ((res = checkExistingConnection(destinationLink, sourceLink, srcPort, destinationPort)) == -1) {
            Socket conn = new Socket(destinationLink, destinationPort, sourceLink, srcPort);
            int i = -1;
            i = findEmpty();
            openfile[i] = new OpenFile(null,"handleAccept");
            socketList[i] = conn;
            //FileDescriptors[i].setFileName("handleAccept");
            res = i;
        }
       try {
            //确定他是请求连接的数据包  同时确保它被发送给正确的人
            UdpPacket SynAckPacket = null;
            
//            System.out.println(mail.toString());
            if (mail.status == UdpPacket.SYN && Machine.networkLink().getLinkAddress() == mail.packet.dstLink) {
                SynAckPacket = new UdpPacket(destinationLink, destinationPort, sourceLink, srcPort, UdpPacket.SYNACK, seq, new byte[0]);
            }
            
            //表示收到的不是连接信息
            if (SynAckPacket == null) {
//                System.out.println("连接");
            	NetKernel.postOffice.waitingDataMessages[port].add(mail);
                return -1;
            }
            
            //回复确认收到
            NetKernel.postOffice.send(SynAckPacket);
            UdpPacket ackPack = NetKernel.postOffice.receive(port);
            while (ackPack == null)
            {
                NetKernel.alarm.waitUntil(RETRANSMIT_INTERVAL);
                ackPack = NetKernel.postOffice.receive(port);
                if(ackPack != null)
                {
                    break;
                }
            }
            //当收到回复是 表示确认连接
            if (ackPack.status == UdpPacket.ACK && Machine.networkLink().getLinkAddress() == mail.packet.dstLink) {
                System.out.print("连接建立：");
            }
        } catch (MalformedPacketException e) {
            return -1;
        }
        return res;
    }

    public int checkExistingConnection(int dest, int src, int srcport, int desport) {
    	//前两个是输入输出
        for (int i = 2; i < openfile.length; i++) {
            //看是否有相同的文件描述符
            if (socketList[i] != null && (socketList[i] instanceof Socket)) {
                Socket con = (Socket) socketList[i];
                //如意已经存在 则返回索引号
                if (con.destinationLink == dest &&
                        con.sourceLink == src &&
                        con.sourcePort == srcport &&
                        con.destinationPort == desport
                        ) {
                    return i;
                }
            }
        }
        //如果不存在返回-1
        return -1;
    }

    private int handleRead(int fileDescriptor, int vaddr, int size) throws MalformedPacketException {
        // Check if the read wants UserProcess's handleRead instead (used for C code
        // statements like printf)
        if (fileDescriptor == 0 || fileDescriptor == 1) {
            return super.handleSyscall(syscallRead, fileDescriptor, vaddr, size, 0);
        }
        
        // Return -1 if the input is invalid
        if (size < 0 || (fileDescriptor >= MAX_SOCKETS || fileDescriptor < 0)
                || socketList[fileDescriptor] == null) {
            return -1;
        }
        
        Socket fd = socketList[fileDescriptor];
        OpenFile fd2 = openfile[fileDescriptor];
        
        //读文件而不是读网络
        if (fd == null&&fd2 != null) {
        	return super.handleSyscall(syscallRead, fileDescriptor, vaddr, size, 0);
        }
        else if(fd == null) {
        	 return -1;
        }
        
        //判断一下收到的文件是不是要求关闭，要求关闭则返回确认
        UdpPacket ask_close = NetKernel.postOffice.getFirst(fd.sourcePort);
        if(ask_close!=null&&ask_close.status == 4) {
        	System.out.print("与客户端"+ask_close.packet.srcLink+"断开连接");
            System.out.println();
        	NetKernel.postOffice.receive(fd.sourcePort);
        	openfile[fileDescriptor]=null;
    		socketList[fileDescriptor].close();
    		socketList[fileDescriptor]=null;
    		return -1;
        }
        
        //需要写入主存的内容
        byte[] buffer = new byte[size];
        int readSize = fd.read(buffer, 0, size);

        if (readSize <= 0)
            return 0;

        //从内存中读出数据  写入 虚拟内存（主存） 然后返回字节数
        int writeSize = writeVirtualMemory(vaddr, buffer, 0, readSize);
        System.out.println("已经读到");
        return writeSize;
    }
    private int handleWrite(int fileDescriptor, int vaddr, int size) {
    	
        // 写控制台或者是写文件
        if(fileDescriptor == 0 || fileDescriptor == 1||(socketList[fileDescriptor] == null&&openfile[fileDescriptor]!=null)) {
            return super.handleSyscall(syscallWrite, fileDescriptor, vaddr, size, 0);
        }

        // Return -1 if the input is invalid
        if(size < 0 || (fileDescriptor >= MAX_SOCKETS || fileDescriptor < 0)
                || socketList[fileDescriptor] == null) {
            return -1;
        }

       Socket fd = socketList[fileDescriptor];
        if (fd == null)
            return -1;
        byte[] buffer = new byte[size];
        //读取主存中的信息  虚拟内存
        int readSize = readVirtualMemory(vaddr, buffer);
        if (readSize == -1)
            return -1;

        //写入文件
        int returnValue = fd.write(buffer, 0, readSize);
        if (returnValue == -1)
            return -1;
        System.out.println("已经写了");
        return returnValue;
    }
    
    private int handleClose(int fileDescriptor) throws MalformedPacketException {
    	Socket socket=null;
    	//删除socket和openfile
    	if(socketList[fileDescriptor]!=null) {
    		socket = socketList[fileDescriptor];
            UdpPacket packet = new UdpPacket(socket.destinationLink, socket.destinationPort,socket. sourceLink, socket.sourcePort,UdpPacket.STP, socket.currentSeqNum, new byte[0]);
            //发送packet
            NetKernel.postOffice.send(packet);
    		openfile[fileDescriptor]=null;
    		socketList[fileDescriptor].close();
    		socketList[fileDescriptor]=null;
    	}
    	
		return 1;
    }

    private static final int
            syscallConnect = 11,
            syscallAccept = 12;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>11</td><td><tt>int  connect(int host, int port);</tt></td></tr>
     * <tr><td>12</td><td><tt>int  accept(int port);</tt></td></tr>
     * </table>
     *
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallConnect:
                return handleConnect(a0, a1);
            case syscallAccept:
                return handleAccept(a0);
            case syscallRead:
			try {
				return handleRead(a0, a1, a2);
			} catch (MalformedPacketException e1) {
				e1.printStackTrace();
			}
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:
			try {
				return handleClose(a0);
			} catch (MalformedPacketException e) {
				e.printStackTrace();
			}

            default:
                return super.handleSyscall(syscall, a0, a1, a2, a3);
        }
    }
}
