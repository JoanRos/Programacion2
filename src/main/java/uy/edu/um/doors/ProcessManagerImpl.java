package uy.edu.um.doors;

import uy.edu.um.Evento;
import uy.edu.um.Proceso;
import uy.edu.um.Usuario;
import uy.edu.um.tad.hash.MyHashImpl;
import uy.edu.um.tad.heap.MyHeapImpl;
import uy.edu.um.tad.list.MyLinkedListImpl;
import uy.edu.um.tad.list.MyList;
import uy.edu.um.tad.queue.EmptyQueueException;
import uy.edu.um.tad.queue.MyQueue;
import uy.edu.um.tad.queue.MyQueueImpl;
import uy.edu.um.tad.stack.MyStackImpl;

import java.util.List;
import java.util.PriorityQueue;

public class ProcessManagerImpl implements ProcessManager{

    //EL DISEÑO DE LA ESTRUCTURA DE ALMACENAMIENTO DEBE IMPLEMENTARSE EN ESTA CLASE EN RELACIÓN CON LAS ENTIDADES QUE DEFINA
    private MyQueueImpl<Proceso> procesosNuevos;
    private MyHeapImpl<Proceso> procesosPendientes; // proceso ID es un integer
    private MyStackImpl<Proceso> procesosFinalizados;
    private int cantidadMaxProcesos; // por letra debemos definirla y controlar elk stack con esto
    private MyHashImpl<Integer,Usuario> usuarios;
    private MyFileManager fm = new MyFileManager();
    private String logFileName;
    private Proceso procesoEnEjecucion;

    public ProcessManagerImpl() {
        this.procesosNuevos = new MyQueueImpl<>();
        this.procesosPendientes = new MyHeapImpl<>(false);
        this.procesosFinalizados = new MyStackImpl<>();
        this.usuarios = new MyHashImpl<>();
        String fecha = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        this.logFileName = "DOORS_PROCESS_LOG_" + fecha;
        this.procesoEnEjecucion = null;
    }

    @Override
    public void loadProcessAndUserData(String processCsvPath, String usersCsvPath) {
      //  System.out.println("IMPLEMENTAR load process and user data");
       // recibimos los path de los CSV que nos da la letra
        // Primero debemos cargar los usuarios
        /// cargar usuarios a TAD USUARIO
        // usamos misma idea que en clase para nombres de cientificos
        MyList<String> lineas = fm.readFile(usersCsvPath);
        for (int i = 1; i < lineas.size() ; i++) {
            String line = lineas.get(i);
            Usuario nuevoUsr = new Usuario();
            String[] partes = line.split(";");
            int userID = Integer.parseInt(partes[0]);
            nuevoUsr.setUid(userID);
            nuevoUsr.setAlias(partes[1]);
            nuevoUsr.setTipo(partes[2].trim());
            // creamops el user, ahora lo agregamos
            usuarios.put(nuevoUsr.getUid(),nuevoUsr);

        }
        // fin de cargas de usuarios
        // Segundo debemos cargar los procesos ya que tendran info de usuario / entonces debe existir el usuario
         // proceso se crea en estado NEW
         // cargar en cola de nuevos proceso

        MyList<String> lineasProcesos = fm.readFile(processCsvPath);
        for (int i = 1; i < lineasProcesos.size() ; i++) {
            String line = lineasProcesos.get(i);
            Proceso nuevoProceso = new Proceso();
            String[] partesProcesos = line.split(";");
            int procesoID = Integer.parseInt(partesProcesos[0]);
            nuevoProceso.setPid( procesoID);
            int usrID = Integer.parseInt(partesProcesos[1]);
            Usuario userPropietario = usuarios.get(usrID);
            nuevoProceso.setUsuarioPropietario(userPropietario);
            nuevoProceso.setNombre(partesProcesos[2]);
            nuevoProceso.setEstado("NEW");
            // cargar los eventos, usar sub string para saltear {} y luego dentro separar #
            String eventosStr = partesProcesos[3].trim();
            eventosStr = eventosStr.substring(1, eventosStr.length() - 1); // sacar { }
            String[] eventoParts = eventosStr.split("# ");
            MyLinkedListImpl<Evento> eventos = new MyLinkedListImpl<>();
            for (int j = 0; j < eventoParts.length; j++) {
                String parte = eventoParts[j];
                String tipo = parte.substring(0, parte.indexOf("[")).replace(":", "").trim();
                String instrStr = parte.substring(parte.indexOf("[") + 1, parte.indexOf("]"));
                String[] instrArray = instrStr.split(", ");
                MyLinkedListImpl<String> instrucciones = new MyLinkedListImpl<>();
                for (int k = 0; k < instrArray.length; k++) {
                    instrucciones.add(instrArray[k].trim());
                }
                eventos.add(new Evento(tipo, instrucciones));
            }
            nuevoProceso.setEventosAsociados(eventos);
            procesosNuevos.enqueue(nuevoProceso);
        }
    }

    @Override
    public void prepareProcesses() {
        while (!procesosNuevos.isEmpty()) {
            Proceso p = null;
            try {
                p = procesosNuevos.dequeue();
            } catch (EmptyQueueException e) {
                throw new RuntimeException(e);
            }
            int prioridad = calcularPrioridad(p);
            p.setPrioridad(prioridad);
            p.setEstado("PENDING");
            procesosPendientes.insert(p);
            escribirLog("NEW PENDING PROCESS: PID=" + p.getPid()
                    + " | " + p.getNombre()
                    + " | USER:" + p.getUsuarioPropietario().getAlias()
                    + " UID:" + p.getUsuarioPropietario().getUid()
                    + " | P=" + prioridad);
        }

    }

    @Override
    public void executeNextProcess() {
        // validar que no haya proceso en ejecucion
        if (procesoEnEjecucion != null) {
            System.out.println("Ya hay un proceso en ejecucion: PID=" + procesoEnEjecucion.getPid());
            return;
        }

        // validar que haya procesos pendientes
        if (procesosPendientes.isEmpty()) {
            System.out.println("No hay procesos pendientes para ejecutar");
            return;
        }

        // extraer el de mayor prioridad del heap
        procesoEnEjecucion = procesosPendientes.remove();
        procesoEnEjecucion.setEstado("RUNNING");

        // construir el mensaje del log
        StringBuilder sb = new StringBuilder();
        sb.append("EXECUTING PROCESS: PID=" + procesoEnEjecucion.getPid()
                + " | USER:" + procesoEnEjecucion.getUsuarioPropietario().getAlias()
                + " UID:" + procesoEnEjecucion.getUsuarioPropietario().getUid());

        // iterar sobre los eventos y agregarlos al mensaje
        MyLinkedListImpl<Evento> eventos = procesoEnEjecucion.getEventosAsociados();
        for (int i = 0; i < eventos.size(); i++) {
            Evento evento = eventos.get(i);
            sb.append("\n EVENT: " + evento.getTipo() + " | Instructions [");
            MyLinkedListImpl<String> instrucciones = evento.getInstrucciones();
            for (int j = 0; j < instrucciones.size(); j++) {
                sb.append(instrucciones.get(j));
                if (j < instrucciones.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]");
        }

        escribirLog(sb.toString());
    }

    @Override
    public void finishProcessOk() {

        System.out.println("IMPLEMENTAR");
    }

    @Override
    public void finishProcessError() {
        System.out.println("IMPLEMENTAR");
    }

    @Override
    public void terminateProcess(int uid) {
        System.out.println("IMPLEMENTAR");
    }

    @Override
    public void printStatus() {
        System.out.println("IMPLEMENTAR");
    }

    @Override
    public void printStatusVerbose() {
        System.out.println("IMPLEMENTAR");
    }

    @Override
    public void printStatusByUser(int uid) {
        System.out.println("IMPLEMENTAR");
    }

    @Override
    public void printStatusByProcess(int pid) {
        System.out.println("IMPLEMENTAR");
    }

    /// funciones auxiliares creadas mayor claridad en funciones solicitadas
    //creamos funciones para secciones de codigo qe se repetian, ejemplo, escribir en el archivo log
    //funcion para escribir log de eventos en el sistema nos basamos en el archivo MyFileManager que brindo el profesor en el practico "practicoHashmap".
    private void escribirLog(String mensaje) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String linea = "[" + timestamp + "]: " + mensaje;
        System.out.println(linea);
        MyList<String> lineas = fm.readFile(logFileName);
        // separar por salto de linea y agregar cada parte
        String[] partes = linea.split("\n");
        for (int i = 0; i < partes.length; i++) {
            lineas.add(partes[i]);
        }
        fm.writeFile(lineas, logFileName);
    }

    //esta funcion devuelve la prioridad para cada proceso que se va a guardar en ProcesosPendientes
    //en funcion de la letra.
    private int calcularPrioridad(Proceso p) {
        int nCPU = 0, nRAM = 0, nDISK = 0;
        MyLinkedListImpl<Evento> eventos = p.getEventosAsociados();
        for (int i = 0; i < eventos.size(); i++) {
            String tipo = eventos.get(i).getTipo();
            if (tipo.equals("CPU")) {
                nCPU++;
            } else if (tipo.equals("RAM")) {
                nRAM++;
            } else if (tipo.equals("DISK")) {
                nDISK++;
            }
        }
        int nEvents = eventos.size();
        int w;
        if (p.getUsuarioPropietario().getTipo().equals("ADMIN")) {
            w = 32;
        } else {
            w = 16;
        }
        return ((8 * nCPU + 2 * nRAM + 2 * nDISK) / nEvents) + w * nEvents;
    }

}
