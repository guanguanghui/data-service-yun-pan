import java.util.*;

public class Main {
    public static void main1(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String firstLine = scanner.nextLine();
        String secondChar = scanner.nextLine();
        int sum = 0;
        for(char e : firstLine.toCharArray()){
            if(secondChar.charAt(0) == e){
                sum ++;
            }
        }
        System.out.println(sum);
    }

    public static Object[] getStrArray(String line){
        ArrayList<String> arr = new ArrayList();
        for(int i=0;i<line.length()/8;i++){
            String str = "";
            for(int j=0; j< 8;j++){
                str = str + line.charAt(i*8+j);
            }
            arr.add(str);
        }

        int s = line.length()/8 * 8 + line.length()%8;
        String endStr = "";
        for(int k=s;k<8;k++){
            if(k<line.length()){
                endStr = endStr + line.charAt(k);
            }else{
                endStr = endStr + "0";
            }
        }
        arr.add(endStr);
        return arr.toArray();
    }

    public static void main3(String[] args){
        Scanner scanner = new Scanner(System.in);
        String firstLine = scanner.nextLine();
        if(firstLine.length()>100){
            firstLine = scanner.nextLine();
        }
        for(Object e:getStrArray(firstLine)){
            System.out.println(e);
        }
        String secondLine = scanner.nextLine();
        if(secondLine.length()>100){
            secondLine = scanner.nextLine();
        }
        for(Object e:getStrArray(secondLine)){
            System.out.println(e);
        }

    }


        public static void main2(String[] args){
            Scanner scanner = new Scanner(System.in);
            while(scanner.hasNext()){
                int n = scanner.nextInt();
                Set<Integer> set = new TreeSet<>();
                for(int i=0;i<n;i++){
                    int num = scanner.nextInt();
                    set.add(num);
                }
                for(Object e: set.toArray()){
                    System.out.println(e);
                }
            }
        }



        public static void main4(String[] args){
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNext()){
                String str1 = scanner.nextLine();
                if (str1.length()%8 != 0){
                    str1 = str1 + "00000000";
                }
                String tmpStr = str1;
                while (tmpStr.length()/8 != 0){
                    System.out.println(tmpStr.substring(0,8));
                    tmpStr = tmpStr.substring(8);
                }
            }
        }

        public static void main(String[] args){
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNext()){
                String str = scanner.nextLine();
                if (str.startsWith("0x") || str.startsWith("0X")){
                    System.out.println(Integer.valueOf(str.substring(2),16));
                }else {
                    System.out.println(Integer.valueOf(str,16));
                }
            }
        }

}
