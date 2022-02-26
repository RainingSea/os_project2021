package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat {
	static BoatGrader bg;
	static boolean boatInO;
	static int num_children_O;
	static int num_alduts_O;
	static int num_children_M;
	static int num_alduts_M;
	static Lock lock;
	static Condition children_condition_o;
	static Condition children_condition_m;
	static Condition alduts_condition_o; 
	static boolean gameover;
	static boolean is_pilot;
	static boolean is_adult_go;
	public static void selfTest() {
		BoatGrader b = new BoatGrader();

//		System.out.println("\n ***Testing Boats with only 2 children***");
//		begin(0, 2, b);

//		 System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
//		 begin(3, 3, b);

		 System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		 begin(1, 2, b);
	}
	

	public static void begin( int adults, int children, BoatGrader b )
    {
    bg = b;
num_children_O=children;
num_alduts_O = adults;
num_alduts_M = 0;
num_children_M = 0;
boatInO = true;
lock = new Lock();
children_condition_o = new Condition(lock);
children_condition_m = new Condition(lock);
alduts_condition_o = new Condition(lock);
gameover = false;
is_pilot = true;
is_adult_go = false;
for(int i = 0;i<adults;i++){//ÿ������Ϊһ���߳�
    new KThread(new Runnable(){
        public void run(){
            AdultItinerary();
        }
    }).fork();;
}

for(int i = 0;i<children;i++){//ÿ��С��Ϊһ���߳�
    new KThread(new Runnable(){
        public void run(){
            ChildItinerary();
        }
    }).fork();;
}
}


static void AdultItinerary(){
    bg.initializeAdult(); 
    lock.acquire();//������
    if(!(is_adult_go&&boatInO)){//������˲��ߣ����ߴ�����O���������˯��
        alduts_condition_o.sleep();
    }
    bg.AdultRowToMolokai();//������˻���M��
    num_alduts_M++;//M���Ĵ�������+1
    num_alduts_O--;//O���Ĵ���������1
    //is_adult_go = false;
    boatInO = false;//������M��
    children_condition_m.wake();//����M���ĺ����߳�
    is_adult_go = false;//��һ�δ��ٵ�O��ʱ���ض���С����
    lock.release();//�ͷ���
}


static void ChildItinerary(){
    bg.initializeChild(); 
    lock.acquire();//������
    while(!gameover){
        if(boatInO){//�������O��
            if(is_adult_go){//����������ߣ���O���Ĵ����̻߳��ѣ�O���ĺ����߳�˯��
                alduts_condition_o.wake();
                children_condition_o.sleep();
            }
            if(is_pilot){//����ǵ�һ��С��������Ϊ����
                bg.ChildRowToMolokai();
                num_children_O--;//O��С������-1
                num_children_M++;//M��С����+1
                is_pilot = false;//��������Ϊfalse
                children_condition_o.wake();//����O��������С���߳�
                children_condition_m.sleep();//���Լ�˯����M��
            }else{//����ǵڶ���С��������Ϊ�ο�

                bg.ChildRideToMolokai();
                boatInO = false;//������Ϊ��M��
                //is_on_O = false;
                num_children_O--;//O����С������-1
                num_children_M++;//M����С������+1
                is_pilot=true;//��������Ϊtrue
                if(num_alduts_O==0&&num_children_O==0){//���O���ĺ��Ӻʹ���������Ϊ0������Ϸ����
                    gameover = true;
                }
                if(gameover){//�����Ϸ���������ӡ�ɹ�����
                    System.out.println("�ɹ����ӣ�����");
                    children_condition_o.sleep();
                }
                if(num_alduts_O!=0&&num_children_O==0){//���O���Ĵ��˻��У���С���߳�Ϊ0������˿���
                    is_adult_go = true;
                }
                children_condition_m.wake();//��M�������������̻߳���
                children_condition_m.sleep();//���Լ�˯����M��
            }
        }else{//�������M��
            bg.ChildRowToOahu();
            //is_on_O = true;
            boatInO = true;//���ô���O��
            num_children_O ++;//O����������+1
            num_children_M --;//M�������߳�����-1
            if(is_adult_go){//������˿����ߣ���O���Ĵ����̻߳���
                alduts_condition_o.wake();
            }else{//���򣬻���O���ĺ����߳�
                children_condition_o.wake();
            }
            children_condition_o.sleep();//���Լ�˯����O��
        }

    }
    lock.release();//�ͷ���
    }

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}
