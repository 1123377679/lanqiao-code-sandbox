package cn.lanqiao.lanqiaocodesandbox.model;

import lombok.Data;

/**
 * @ Author: 李某人
 * @ Date: 2024/12/10/22:27
 * @ Description:
 */
@Data
public class ExecuteMessage {

    private Integer exitValue;

    private String message;

    private String errorMessage;

    private Long time;

    private Long memory;
}
