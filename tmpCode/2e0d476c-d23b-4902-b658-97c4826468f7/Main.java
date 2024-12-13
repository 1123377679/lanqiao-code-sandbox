public class Main {
    public static void main(String[] args) {
        //实际在OJ系统中，对用户输入的代码一般都会有要求,便于系统的统一处理,我们把用户输入的代码类名限制成Main
        Integer a = Integer.parseInt(args[0]);
        Integer b = Integer.parseInt(args[1]);
        System.out.println("结果是:"+(a+b));
    }
}
