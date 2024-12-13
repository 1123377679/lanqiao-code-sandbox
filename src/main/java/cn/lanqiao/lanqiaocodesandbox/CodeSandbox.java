package cn.lanqiao.lanqiaocodesandbox;


import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeRequest;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeResponse;

/**
 * @ Author: 李某人
 * @ Date: 2024/11/30/13:32
 * @ Description:
 * 代码沙箱:接收代码->编译代码(javac)->执行代码(java)
 *
 */
public interface CodeSandbox {
     //执行代码的接口
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
