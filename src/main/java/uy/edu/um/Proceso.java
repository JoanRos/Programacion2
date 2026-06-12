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
public class Proceso implements Comparable<Proceso> {
    private Integer pid;
    private String nombre;
    private Usuario usuarioPropietario;
    private Integer prioridad;
    private String estado; // por letra son NEW,PENDING,RUNNING FINISHED
    private MyLinkedListImpl<Evento> eventosAsociados;
    @Override
    public int compareTo(Proceso otro) {
        return this.prioridad.compareTo(otro.prioridad);
    }
    private String tipoFinalizacion; // OK, ERROR, TERMINATED
    private Usuario terminadoPor; // solo se usa si es TERMINATED

}
