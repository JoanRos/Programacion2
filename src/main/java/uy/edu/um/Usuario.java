package uy.edu.um;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Usuario {
    private Integer uid;
    private String alias;
    private String tipo; /// por letra este puede ser solo GENERIC O ADMIN consu;ltas si hacemos tipo de dato puntual

}
