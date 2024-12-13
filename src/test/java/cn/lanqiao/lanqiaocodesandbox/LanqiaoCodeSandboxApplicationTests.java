package cn.lanqiao.lanqiaocodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.lanqiao.lanqiaocodesandbox.security.MySecurityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.Charset;

@SpringBootTest
class LanqiaoCodeSandboxApplicationTests {

    @Test
    void contextLoads() {
    }

    public static void main(String[] args) {
        System.setSecurityManager(new MySecurityManager());
        FileUtil.writeString("a","aaa", Charset.defaultCharset());
    }
}
