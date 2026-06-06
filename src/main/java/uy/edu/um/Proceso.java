package uy.edu.um;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uy.edu.um.tad.hash.MyHashImpl;
import uy.edu.um.tad.list.MyLinkedListImpl;

import java.awt.*;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Proceso {
    private Integer pid;
    private String nombre;
    private Usuario usuarioPriopietario;
    private Integer prioridad;
    private String estado; // por letra son NEW,PENDING,RUNNING FINISHED
    private MyLinkedListImpl<Evento> eventosAsociados;

//juandiego

}
