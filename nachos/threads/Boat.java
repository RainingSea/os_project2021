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
for(int i = 0;i<adults;i++){//每个大人为一个线程
    new KThread(new Runnable(){
        public void run(){
            AdultItinerary();
        }
    }).fork();;
}

for(int i = 0;i<children;i++){//每个小孩为一个线程
    new KThread(new Runnable(){
        public void run(){
            ChildItinerary();
        }
    }).fork();;
}
}


static void AdultItinerary(){
    bg.initializeAdult(); 
    lock.acquire();//申请锁
    if(!(is_adult_go&&boatInO)){//如果大人不走，或者船不在O岛，则大人睡眠
        alduts_condition_o.sleep();
    }
    bg.AdultRowToMolokai();//否则大人划至M岛
    num_alduts_M++;//M岛的大人数量+1
    num_alduts_O--;//O岛的大人数量—1
    //is_adult_go = false;
    boatInO = false;//船改至M岛
    children_condition_m.wake();//唤醒M岛的孩子线程
    is_adult_go = false;//下一次船再到O岛时，必定是小孩走
    lock.release();//释放锁
}


static void ChildItinerary(){
    bg.initializeChild(); 
    lock.acquire();//申请锁
    while(!gameover){
        if(boatInO){//如果船在O岛
            if(is_adult_go){//如果大人能走，则将O岛的大人线程唤醒，O岛的孩子线程睡眠
                alduts_condition_o.wake();
                children_condition_o.sleep();
            }
            if(is_pilot){//如果是第一个小孩，则设为舵手
                bg.ChildRowToMolokai();
                num_children_O--;//O岛小孩数量-1
                num_children_M++;//M岛小孩数+1
                is_pilot = false;//将舵手设为false
                children_condition_o.wake();//唤醒O岛的其他小孩线程
                children_condition_m.sleep();//让自己睡眠在M岛
            }else{//如果是第二个小孩，则设为游客

                bg.ChildRideToMolokai();
                boatInO = false;//将船改为在M岛
                //is_on_O = false;
                num_children_O--;//O岛的小孩数量-1
                num_children_M++;//M岛的小孩数量+1
                is_pilot=true;//将舵手设为true
                if(num_alduts_O==0&&num_children_O==0){//如果O岛的孩子和大人数量均为0，则游戏结束
                    gameover = true;
                }
                if(gameover){//如果游戏结束，则打印成功过河
                    System.out.println("成功过河！！！");
                    children_condition_o.sleep();
                }
                if(num_alduts_O!=0&&num_children_O==0){//如果O岛的大人还有，但小孩线程为0，则大人可走
                    is_adult_go = true;
                }
                children_condition_m.wake();//将M岛的其他孩子线程唤醒
                children_condition_m.sleep();//将自己睡眠在M岛
            }
        }else{//如果船在M岛
            bg.ChildRowToOahu();
            //is_on_O = true;
            boatInO = true;//设置船在O岛
            num_children_O ++;//O岛孩子数量+1
            num_children_M --;//M岛孩子线程数量-1
            if(is_adult_go){//如果大人可以走，则将O岛的大人线程唤醒
                alduts_condition_o.wake();
            }else{//否则，唤醒O岛的孩子线程
                children_condition_o.wake();
            }
            children_condition_o.sleep();//让自己睡眠在O岛
        }

    }
    lock.release();//释放锁
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
