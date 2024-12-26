package cn.lanqiao.lanqiaocodesandbox;

import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeRequest;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;


/**
 * @ Author: 李某人
 * @ Date: 2024/12/10/21:57
 * @ Description:
 *
 * 守护线程:用一个新的线程，判断一个正在运行的线程是否超时
 */
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate{


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
