package co.edu.konradlorenz.kapp.common.dto;

import lombok.Data;
import java.util.Date;

/**
 * DTO para transferencia de datos de Empleado.
 */
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
