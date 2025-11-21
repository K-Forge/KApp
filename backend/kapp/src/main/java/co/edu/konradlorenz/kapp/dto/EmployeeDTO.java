package co.edu.konradlorenz.kapp.dto;

import lombok.Data;
import java.util.Date;

@Data
public class EmployeeDTO {
    private Long id;
    private Long memberId;
    private String employeeCode;
    private String employeeType;
    private String employeeRole;
    private String contractType;
    private Date hireDate;
    private Double salary;
    private String status;
}
