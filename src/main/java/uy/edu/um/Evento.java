package uy.edu.um;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uy.edu.um.tad.list.MyLinkedListImpl;

import java.awt.*;
import java.util.LinkedList;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Evento {

    private String tipo;
    /// segun letta CPU, RAM DISK
    private MyLinkedListImpl<String> instrucciones;
}