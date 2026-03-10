package co.edu.konradlorenz.kapp.dto;

import lombok.Data;
import java.util.Date;

@Data
public class PersonDTO {
    private Long id;
    private Integer identificationNumber;
    private String identificationType;
    private String firstName;
    private String lastName;
    private String phone;
    private String homeAddress;
    private Date birthDate;
}
