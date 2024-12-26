package cn.lanqiao.lanqiaocodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.ArrayUtil;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeRequest;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeResponse;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteMessage;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate{
    private static final Long TIME_OUT = 5000L;
    private static final boolean FIRST_INIT = true;

    // public static void main(String[] args) {
    //     JavaDockerCodeSandboxOld javaNativeCodeSandbox = new JavaDockerCodeSandboxOld();
    //     ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
    //     executeCodeRequest.setInputList(Arrays.asList("1 2","3 4"));
    //     //传入用户写的代码
    //     String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
    //     // String code = ResourceUtil.readStr("testCode/errorCode/RunFileError.java", StandardCharsets.UTF_8);
    //     executeCodeRequest.setCode(code);
    //     executeCodeRequest.setLanguage("java");
    //     ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
    //     System.out.println(executeCodeResponse);
    // }

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String absolutePath = userCodeFile.getParentFile().getAbsolutePath();
        //覆盖父类的方法
        //3.拉取镜像
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        //拉取jdk8的镜像
        String image = "openjdk:8-alpine";
        if (FIRST_INIT){
            //用户第一次进来需要拉取镜像，其他的时候咱们都不需要拉取镜像
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像:"+item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }
        System.out.println("下载完成");
        //4.创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        // 设置容器的内存限制为 100MB
        hostConfig.withMemory(100 * 1000 * 1000L);

        // 设置容器的交换内存为 0，禁用交换内存
        hostConfig.withMemorySwap(0L);

        // 限制容器只能使用 1 个 CPU 核心
        hostConfig.withCpuCount(1L);

        // 设置安全选项，使用 seccomp 配置来限制容器的系统调用
//        hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));

        // 将主机的 userCodeParentPath 目录挂载到容器的 /app 目录
        hostConfig.setBinds(new Bind(absolutePath, new Volume("/app")));

        // 执行容器创建命令，配置各种参数：
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)           // 设置主机配置
                .withNetworkDisabled(true)           // 禁用网络访问
                .withReadonlyRootfs(true)            // 将根文件系统设置为只读
                .withAttachStdin(true)               // 允许附加标准输入
                .withAttachStderr(true)              // 允许附加标准错误输出
                .withAttachStdout(true)              // 允许附加标准输出
                .withTty(true)                       // 分配一个伪 TTY
                .exec();                             // 执行创建命令

        // 打印容器创建的响应信息
        System.out.println(createContainerResponse);
        //去启动容器执行命令并且获取结果
        String containerId = createContainerResponse.getId();
        dockerClient.startContainerCmd(containerId).exec();
        //执行命令并获取结果
        //docker exec 'docker镜像名字' java -cp /app Main 1 3 2 4

        List<ExecuteMessage> executeMessageList = new ArrayList<>();//用来存储输出信息
        for (String inputArgs : inputList){
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            // 构建完整的命令数组：
            // - "java": 使用 Java 运行程序
            // - "-cp": 指定类路径
            // - "/app": 类路径为容器中的 /app 目录
            // - "Main": 要运行的主类名
            // - inputArgsArray: 附加用户的输入参数
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);

            // 创建在容器中执行命令的请求：
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)               // 设置要执行的命令数组
                    .withAttachStderr(true)         // 附加标准错误输出
                    .withAttachStdin(true)          // 附加标准输入
                    .withAttachStdout(true)         // 附加标准输出
                    .exec();                        // 执行命令创建
            // 打印创建的执行命令信息
            System.out.println("创建执行命令：" + execCreateCmdResponse);
            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};//运行结果的信息
            final String[] errorMessage = {null};//运行错误结果的信息
            long time = 0L;//记录运行时间
            final long[] maxMemory ={0L};//记录最大内存
            //执行命令
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(){
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)){
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果:"+ errorMessage[0]);
                    }else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果:"+ message[0]);
                    }
                    super.onNext(frame);
                }
            };

            //记录内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用:"+statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });
            stopWatch.start();
            try {
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            executeMessage.setTime(time);
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }
        return executeMessageList;
    }
}
