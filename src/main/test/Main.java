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

        public static void main5(String[] args){
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

    public static void main6(String[] args){
//        Scanner sc = new Scanner(System.in);
//        while(sc.hasNext()){
//            Float num = sc.nextFloat();
//            System.out.println(num.intValue() + (int)((num-num.intValue())*10)/5);
//        }

        Scanner sc = new Scanner(System.in);
        int num = Integer.valueOf(sc.nextLine());
        Map<Integer,Integer> map = new HashMap<>();
        for(int i=0;i<num;i++){
            String line = sc.nextLine();
            String[]pair = line.split("\\s+");
            Integer key = Integer.valueOf(pair[0]);
            Integer value = Integer.valueOf(pair[1]);
            value = map.get(key)!=null? value+map.get(key):value;
            map.put(key,value);
        }
//        Integer[] keys = (Integer[]) map.keySet().toArray();
//        Arrays.sort(keys);
        for(Integer e: map.keySet()){
            System.out.println(e + " " + map.get(e));
        }
    }

    public static void reverse(int num){
        LinkedHashSet<Integer> list = new LinkedHashSet<>();
        while(num%10 != 0){
            list.add(num%10);
            num = num/10;
        }
        for(Integer e:list){
            System.out.print(e);
        }
    }

    public static void main7(String[] args){
        Scanner sc = new Scanner(System.in);
        int num = Integer.valueOf(sc.nextLine());
        reverse(num);
    }

    public static void main8(String[] args){
        Scanner sc = new Scanner(System.in);
        String line = sc.nextLine();
        HashSet<Character> set = new HashSet<>();
        for(Character e: line.toCharArray()){
            if (e.charValue() != 10){
                set.add(e);
            }
        }
        System.out.println(set.size());
    }

    public static void main9(String[] args){
        Scanner sc = new Scanner(System.in);
        while(sc.hasNext()){
            String num = sc.nextLine();
            char[] chars = num.toCharArray();
            for(int i=chars.length-1;i>=0;i--){
                System.out.print(chars[i]);
            }
        }
    }

    public static void main10(String[] args){
        Scanner sc = new Scanner(System.in);
        while(sc.hasNext()){
            String line = sc.nextLine();
            String[] words = line.split("\\s+");
            for(int i=words.length-1;i>=0;i--){
                if (i == 0) {
                    System.out.print(words[i]);
                }else{
                    System.out.print(words[i] + " ");
                }
            }
        }
    }

    public static void main11(String[] args){
        Scanner sc = new Scanner(System.in);
        while (sc.hasNext()){
            int num = Integer.parseInt(sc.nextLine()) ;
            Vector<String> set = new Vector<>();
            for(int i=0;i<num;i++){
                String line = sc.nextLine();
                set.add(line);
            }
            Collections.sort(set);
            for(String e: set){
                System.out.println(e);
            }
        }
    }

    public static void main12(String[] args){
        Scanner sc = new Scanner(System.in);
        while (sc.hasNext()){
            Integer num = sc.nextInt();
            int sum =0;
            while(num != 0){
                if(num%2 !=0){
                    sum+=1;
                }
                num = num/2;
            }
            System.out.println(sum);
        }
    }

    public static void main13(String[] args){
        Scanner sc = new Scanner(System.in);
        while(sc.hasNext()){
            String line1 = sc.nextLine();
            String[] line1Arr = line1.split("\\s+");
            // 总钱数
            int N = Integer.parseInt(line1Arr[0]);
            int m = Integer.parseInt(line1Arr[1]);

            for(int i=0;i<m;i++){
                String line = sc.nextLine();
                String[] lineArr = line.split("\\s+");
                // 编号 i
                // 价格
                int v = Integer.parseInt(line1Arr[0]);
                // 重要度
                int p = Integer.parseInt(line1Arr[1]);
                // 0主件 非零表示附件
                int q = Integer.parseInt(line1Arr[2]);
            }
        }
    }

    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        while(sc.hasNext()){
            String line = sc.nextLine();
            String[] strArr = line.split(";");
            Map<Character,Integer> map = new HashMap<>();
            for(String e:strArr){
                if(e!=null && e.length() >= 2){
                    System.out.println(e);
                    char c = e.charAt(0);
                    Integer num = Integer.valueOf(e.substring(1));
                    switch(c){
                        case 'A':
                        case 'D':
                        case 'W':
                        case 'S':
                            Integer value = map.get(c)!=null?num + map.get(c):num;
                            map.put(c,value);
                            break;
                        default:
                            break;
                    }
                }
            }
            int x=0,y=0;
            for(Character c:map.keySet()){
                if(c == 'A'){
                    x-=map.get(c);
                }else if(c == 'D'){
                    x+=map.get(c);
                }else if(c == 'W'){
                    y+=map.get(c);
                }else if(c == 'S'){
                    y-=map.get(c);
                }
            }
            System.out.println(x +","+y);
        }
    }

}
