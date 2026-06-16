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
import uy.edu.um.tad.stack.EmptyStackException;
import uy.edu.um.tad.stack.MyStackImpl;

import java.util.List;
import java.util.PriorityQueue;

public class ProcessManagerImpl implements ProcessManager{

    //EL DISEÑO DE LA ESTRUCTURA DE ALMACENAMIENTO DEBE IMPLEMENTARSE EN ESTA CLASE EN RELACIÓN CON LAS ENTIDADES QUE DEFINA
    private MyQueueImpl<Proceso> procesosNuevos;
    private MyHeapImpl<Proceso> procesosPendientes; // proceso ID es un integer
    private MyStackImpl<Proceso> procesosFinalizados; // por letra debemos definirla y controlar el stack "procesosFinalizados" con MAX_FINISHED_PROCESS_ON_RAM
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
        // verificamos si hay una ejecucion en curso, porque por letra es uno a la vez
        if (procesoEnEjecucion == null) {
            System.out.println("No hay proceso en ejecucion para finalizar.");
            return;
        }
        procesoEnEjecucion.setEstado("FINISHED");
        procesoEnEjecucion.setTipoFinalizacion("OK");

        escribirLog("ENDING PROCESS: PID=" + procesoEnEjecucion.getPid() + " | STATE: OK"); //validar la impresion en log con esta forma

        //se verifica si la pila esta llena antes de agregar el proceso finished
        if (procesosFinalizados.size() == MAX_FINISHED_PROCESS_ON_RAM) {
            // la pila esta llena, hay que registrar en el log y vaciar.
            escribirLog("Finished process stack overflow");
            while (!procesosFinalizados.isEmpty()) { //usamos while para vaciar el stack con pop()
                Proceso finalizado = null;
                try {
                    finalizado = procesosFinalizados.pop();
                } catch (EmptyStackException e) {
                    throw new RuntimeException(e);
                }
                escribirLog("PID=" + finalizado.getPid() + " " + finalizado.getNombre() + " | STATE: " + finalizado.getTipoFinalizacion() + " | USER:" + finalizado.getUsuarioPropietario().getAlias() + " UID:" + finalizado.getUsuarioPropietario().getUid());
            }
        }
        procesosFinalizados.push(procesoEnEjecucion);  //proceso finalizado agregado en el stack
        procesoEnEjecucion = null;
    }

    @Override
    public void finishProcessError() {
        //finishPrecessError es la misma funcion que finishProcessOk solo que al estado se le asigna "ERROR"
        // verificamos si hay una ejecucion en curso, porque por letra es uno a la vez
        if (procesoEnEjecucion == null) {
            System.out.println("No hay proceso en ejecucion para finalizar.");
            return;
        }
        procesoEnEjecucion.setEstado("FINISHED");
        procesoEnEjecucion.setTipoFinalizacion("ERROR");

        escribirLog("ENDING PROCESS: PID=" + procesoEnEjecucion.getPid() + " | STATE: ERROR"); //validar la impresion en log con esta forma

        //se verifica si la pila esta llena antes de agregar el proceso finished
        if (procesosFinalizados.size() == MAX_FINISHED_PROCESS_ON_RAM) {
            // la pila esta llena, hay que registrar en el log y vaciar.
            escribirLog("Finished process stack overflow");
            while (!procesosFinalizados.isEmpty()) { //usamos while para vaciar el stack con pop()
                Proceso finalizado = null;
                try {
                    finalizado = procesosFinalizados.pop();
                } catch (EmptyStackException e) {
                    throw new RuntimeException(e);
                }
                escribirLog("PID=" + finalizado.getPid() + " " + finalizado.getNombre() + " | STATE: " + finalizado.getTipoFinalizacion() + " | USER:" + finalizado.getUsuarioPropietario().getAlias() + " UID:" + finalizado.getUsuarioPropietario().getUid());
            }
        }
        procesosFinalizados.push(procesoEnEjecucion);  //proceso finalizado agregado en el stack
        procesoEnEjecucion = null;
    }

    @Override
    public void terminateProcess(int uid) {
        if (procesoEnEjecucion == null) {
            System.out.println("No hay proceso en ejecucion para finalizar.");
            return;
        }

        // buscamos el usuario que fuerza la terminacion por su UID
        Usuario responsable = usuarios.get(uid);
        if (responsable == null) {
            System.out.println("No existe usuario con UID=" + uid);
            return;
        }

        procesoEnEjecucion.setEstado("FINISHED");
        procesoEnEjecucion.setTipoFinalizacion("TERMINATED");
        // guardamos quien lo termino, dato extra que pide la letra
        procesoEnEjecucion.setTerminadoPor(responsable);

        // el log tiene formato distinto cuando es TERMINATED, segun la letra:
        // [timestamp]: ENDING PROCESS: PID=123 | STATE: TERMINATED by USER:alias UID:65
        escribirLog("ENDING PROCESS: PID=" + procesoEnEjecucion.getPid()
                + " | STATE: TERMINATED by USER:" + responsable.getAlias()
                + " UID:" + responsable.getUid());

        if (procesosFinalizados.size() == MAX_FINISHED_PROCESS_ON_RAM) {
            escribirLog("Finished process stack overflow");
            while (!procesosFinalizados.isEmpty()) {
                Proceso finalizado = null;
                try {
                    finalizado = procesosFinalizados.pop();
                } catch (EmptyStackException e) {
                    throw new RuntimeException(e);
                }
                escribirLog("PID=" + finalizado.getPid()
                        + " " + finalizado.getNombre()
                        + " | STATE: " + finalizado.getTipoFinalizacion()
                        + " | USER:" + finalizado.getUsuarioPropietario().getAlias()
                        + " UID:" + finalizado.getUsuarioPropietario().getUid());
            }
        }

        procesosFinalizados.push(procesoEnEjecucion);
        procesoEnEjecucion = null;
    }

    @Override
    public void printStatus() {
        ///  para esta implementacion nos basamos 100% en imprimit en el orden de letra
        ///  basicamente queremos recorrretr los procesos en ejecuccion, pendientes y funalizados e imprimirlos en ese orden
        ///  para cada seccion recorremos el tad correspondiente. Importante el de en ejecuccion es uno solo, es un objeto ya que se ejecuta de uno a la vez


        System.out.println("PROCESS STATUS");

        // ---- EXECUTING ----
        // procesoEnEjecucion es una variable directa, acceso simple O(1)
        System.out.println("EXECUTING:");
        if (procesoEnEjecucion != null) {
            System.out.println("\tPID=" + procesoEnEjecucion.getPid()
                    + " | " + procesoEnEjecucion.getNombre()
                    + " | USER:" + procesoEnEjecucion.getUsuarioPropietario().getAlias()
                    + " UID:" + procesoEnEjecucion.getUsuarioPropietario().getUid()
                    + " | P=" + procesoEnEjecucion.getPrioridad());
        } else {
            System.out.println("\t(ninguno)");
        }

        // ---- PENDING ----
        // No podemos iterar el heap directamente sin modificar el TAD.
        // Solucion: vaciamos con remove() (que devuelve siempre el de mayor prioridad),
        // imprimimos, guardamos en stack auxiliar, y luego restauramos el heap.
        // Ventaja: los pendientes quedan ordenados de mayor a menor prioridad,
        // igual que el ejemplo de la letra.
        System.out.println("PENDING:");
        if (procesosPendientes.isEmpty()) {
            System.out.println("\t(ninguno)");
        } else {
            // Stack auxiliar para guardar los procesos mientras vaciamos el heap
            MyStackImpl<Proceso> auxiliarHeap = new MyStackImpl<>();

            // Paso 1: vaciamos el heap imprimiendo y guardando en auxiliar
            while (!procesosPendientes.isEmpty()) {
                Proceso p = procesosPendientes.remove(); // saca siempre el de mayor prioridad
                System.out.println("\tPID=" + p.getPid()
                        + " | " + p.getNombre()
                        + " | USER:" + p.getUsuarioPropietario().getAlias()
                        + " UID:" + p.getUsuarioPropietario().getUid()
                        + " | P=" + p.getPrioridad());
                auxiliarHeap.push(p);
            }

            // Paso 2: restauramos el heap insertando desde el auxiliar
            // insert() se encarga de reordenar el heap correctamente
            // no importa el orden en que insertamos
            while (!auxiliarHeap.isEmpty()) {
                try {
                    procesosPendientes.insert(auxiliarHeap.pop());
                } catch (Exception e) {
                    break;
                }
            }
        }

        // ---- FINISHED ----
        // La letra pide orden inverso al de finalizacion = el mas reciente primero.
        // El stack tiene el mas reciente en el tope, entonces pop() nos da ese orden.
        // Problema: pop() destruye el stack, necesitamos restaurarlo.
        // Solucion: misma logica que el heap pero con stack auxiliar.
        //   - Pop del original e imprimimos -> push al auxiliar (invierte orden)
        //   - Pop del auxiliar -> push al original (restaura orden original)
        System.out.println("FINISHED:");
        if (procesosFinalizados.isEmpty()) {
            System.out.println("\t(ninguno)");
        } else {
            MyStackImpl<Proceso> auxiliarStack = new MyStackImpl<>();

            // Paso 1: vaciamos el stack original imprimiendo e invirtiendo en auxiliar
            while (!procesosFinalizados.isEmpty()) {
                try {
                    Proceso p = procesosFinalizados.pop(); // saca el mas reciente
                    System.out.println("\tPID=" + p.getPid()
                            + " " + p.getNombre()
                            + " | STATE: " + p.getEstado()
                            + " | USER:" + p.getUsuarioPropietario().getAlias()
                            + " UID:" + p.getUsuarioPropietario().getUid());
                    auxiliarStack.push(p);
                } catch (Exception e) {
                    break;
                }
            }

            // Paso 2: restauramos el stack original desde el auxiliar
            // el auxiliar tiene el mas antiguo en el tope, al hacer pop+push
            // el original queda exactamente igual que antes
            while (!auxiliarStack.isEmpty()) {
                try {
                    procesosFinalizados.push(auxiliarStack.pop());
                } catch (Exception e) {
                    break;
                }
            }
        }
    }

    @Override
    public void printStatusVerbose() {
        ///  aca reutilizamos gran parte del codigo de arriba
        /// solo que agregamos un llamado a una funcion para que al imprimir cada proceso imprima susu eventos asociados
        ///  IMPORTANTE, como imprimimos eventos de procesos pendientes, en ejecuccion y finalizados, es mejor hacer una funcion aparte
        /// que imprima los eventos de un proceso, asi queda mas prolijo el codigo ee y la llamamos, pudiendo reutilizar el codigo de arriba
      // creamos la de  ---> printEventosDelProceso esta abjo

        System.out.println("PROCESS STATUS (VERBOSE)");

        // ---- EXECUTING ----
        System.out.println("EXECUTING:");
        if (procesoEnEjecucion != null) {
            System.out.println("\tPID=" + procesoEnEjecucion.getPid()
                    + " | " + procesoEnEjecucion.getNombre()
                    + " | USER:" + procesoEnEjecucion.getUsuarioPropietario().getAlias()
                    + " UID:" + procesoEnEjecucion.getUsuarioPropietario().getUid()
                    + " | P=" + procesoEnEjecucion.getPrioridad());
            // Unica diferencia con printStatus: mostramos eventos
            printEventosDelProceso(procesoEnEjecucion);
        } else {
            System.out.println("\t(ninguno)");
        }

        // ---- PENDING ----
        // Misma logica de vaciado y restauracion del heap que en printStatus()
        System.out.println("PENDING:");
        if (procesosPendientes.isEmpty()) {
            System.out.println("\t(ninguno)");
        } else {
            MyStackImpl<Proceso> auxiliarHeap = new MyStackImpl<>();

            // Paso 1: vaciamos el heap imprimiendo con eventos y guardando en auxiliar
            while (!procesosPendientes.isEmpty()) {
                Proceso p = procesosPendientes.remove();
                System.out.println("\tPID=" + p.getPid()
                        + " | " + p.getNombre()
                        + " | USER:" + p.getUsuarioPropietario().getAlias()
                        + " UID:" + p.getUsuarioPropietario().getUid()
                        + " | P=" + p.getPrioridad());
                // Unica diferencia con printStatus: mostramos eventos
                printEventosDelProceso(p);
                auxiliarHeap.push(p);
            }

            // Paso 2: restauramos el heap
            while (!auxiliarHeap.isEmpty()) {
                try {
                    procesosPendientes.insert(auxiliarHeap.pop());
                } catch (Exception e) {
                    break;
                }
            }
        }

        // ---- FINISHED ----
        // Misma logica de pila auxiliar que en printStatus()
        System.out.println("FINISHED:");
        if (procesosFinalizados.isEmpty()) {
            System.out.println("\t(ninguno)");
        } else {
            MyStackImpl<Proceso> auxiliarStack = new MyStackImpl<>();

            // Paso 1: vaciamos imprimiendo con eventos e invertimos en auxiliar
            while (!procesosFinalizados.isEmpty()) {
                try {
                    Proceso p = procesosFinalizados.pop();
                    System.out.println("\tPID=" + p.getPid()
                            + " " + p.getNombre()
                            + " | STATE: " + p.getEstado()
                            + " | USER:" + p.getUsuarioPropietario().getAlias()
                            + " UID:" + p.getUsuarioPropietario().getUid());
                    // Unica diferencia con printStatus: mostramos eventos
                    printEventosDelProceso(p);
                    auxiliarStack.push(p);
                } catch (Exception e) {
                    break;
                }
            }

            // Paso 2: restauramos el stack original
            while (!auxiliarStack.isEmpty()) {
                try {
                    procesosFinalizados.push(auxiliarStack.pop());
                } catch (Exception e) {
                    break;
                }
            }
        }
    }

    @Override
    public void printStatusByUser(int uid) {

        ///  aca la idea es un poco similar solo con un cuidadoo
        ///  promero validamos si existe el user, de no existir avisamos con print en p[antalla
        ///  de existir usamos codsifgo de arriba como base, pero con el condicional al momento de imprimir que el usuario del proceso sea el que nos pasaron por parametro al llamar

        // Buscamos el usuario en el hash por su UID, operacion O(1)
        Usuario usuario = usuarios.get(uid);
        if (usuario == null) {
            System.out.println("No existe usuario con UID=" + uid);
            return;
        }
        System.out.println("PROCESOS DEL USUARIO: " + usuario.getAlias() + " UID:" + uid);

        // ---- EXECUTING ----
        // Verificamos si el proceso en ejecucion pertenece a este usuario
        if (procesoEnEjecucion != null
                && procesoEnEjecucion.getUsuarioPropietario().getUid().equals(uid)) {
            System.out.println("[RUNNING] PID=" + procesoEnEjecucion.getPid()
                    + " | " + procesoEnEjecucion.getNombre()
                    + " | P=" + procesoEnEjecucion.getPrioridad());
        }

        // ---- PENDING ----
        // Misma logica de vaciado y restauracion del heap.
        // Solo imprimimos los que coinciden con el UID pero guardamos TODOS
        // en el auxiliar para poder restaurar el heap completo al final
        MyStackImpl<Proceso> auxiliarHeap = new MyStackImpl<>();

        while (!procesosPendientes.isEmpty()) {
            Proceso p = procesosPendientes.remove();
            // Solo imprimimos si es del usuario buscado, pero guardamos siempre
            if (p.getUsuarioPropietario().getUid().equals(uid)) {
                System.out.println("[PENDING] PID=" + p.getPid()
                        + " | " + p.getNombre()
                        + " | P=" + p.getPrioridad());
            }
            auxiliarHeap.push(p);
        }

        // Restauramos el heap completo
        while (!auxiliarHeap.isEmpty()) {
            try {
                procesosPendientes.insert(auxiliarHeap.pop());
            } catch (Exception e) {
                break;
            }
        }

        // ---- FINISHED ----
        // Misma logica de pila auxiliar.
        // Solo imprimimos los que coinciden con el UID pero guardamos TODOS
        MyStackImpl<Proceso> auxiliarStack = new MyStackImpl<>();

        while (!procesosFinalizados.isEmpty()) {
            try {
                Proceso p = procesosFinalizados.pop();
                // Solo imprimimos si es del usuario buscado, pero guardamos siempre
                if (p.getUsuarioPropietario().getUid().equals(uid)) {
                    System.out.println("[FINISHED] PID=" + p.getPid()
                            + " | " + p.getNombre()
                            + " | STATE: " + p.getEstado());
                }
                auxiliarStack.push(p);
            } catch (Exception e) {
                break;
            }
        }

        // Restauramos el stack completo
        while (!auxiliarStack.isEmpty()) {
            try {
                procesosFinalizados.push(auxiliarStack.pop());
            } catch (Exception e) {
                break;
            }
        }


    }

    @Override
    public void printStatusByProcess(int pid) {

        // Buscamos el proceso por PID en los 3 lugares posibles en memoria:
        // EXECUTING (variable directa), PENDING (heap) y FINISHED (stack)
        // En cada caso usamos la misma logica de vaciado/restauracion con auxiliar
        // Guardamos el proceso encontrado en "encontrado" y recien al final imprimimos,
        // para no mezclar el output con la restauracion de las estructuras

        // ---- EXECUTING ----
        // Acceso directo O(1), si lo encontramos imprimimos y salimos
        if (procesoEnEjecucion != null && procesoEnEjecucion.getPid().equals(pid)) {
            printProcesoDetalle(procesoEnEjecucion);
            return;
        }

        // ---- PENDING ----
        // Vaciamos el heap con remove() revisando cada proceso, restauramos con insert()
        // Guardamos todos en auxiliar aunque encontremos el que buscamos,
        // porque necesitamos restaurar el heap completo
        MyStackImpl<Proceso> auxiliarHeap = new MyStackImpl<>();
        Proceso encontrado = null;

        while (!procesosPendientes.isEmpty()) {
            Proceso p = procesosPendientes.remove();
            if (p.getPid().equals(pid)) {
                encontrado = p; // lo guardamos pero NO imprimimos todavia
            }
            auxiliarHeap.push(p); // guardamos siempre para restaurar
        }
        // Restauramos el heap antes de imprimir cualquier cosa
        while (!auxiliarHeap.isEmpty()) {
            try {
                procesosPendientes.insert(auxiliarHeap.pop());
            } catch (Exception e) {
                break;
            }
        }
        // Si lo encontramos en pendientes, imprimimos y salimos
        if (encontrado != null) {
            printProcesoDetalle(encontrado);
            return;
        }

        // ---- FINISHED ----
        // Misma logica con stack auxiliar
        MyStackImpl<Proceso> auxiliarStack = new MyStackImpl<>();

        while (!procesosFinalizados.isEmpty()) {
            try {
                Proceso p = procesosFinalizados.pop();
                if (p.getPid().equals(pid)) {
                    encontrado = p; // guardamos pero NO imprimimos todavia
                }
                auxiliarStack.push(p); // guardamos siempre para restaurar
            } catch (Exception e) {
                break;
            }
        }
        // Restauramos el stack antes de imprimir
        while (!auxiliarStack.isEmpty()) {
            try {
                procesosFinalizados.push(auxiliarStack.pop());
            } catch (Exception e) {
                break;
            }
        }

        // Recien aca imprimimos el resultado final
        if (encontrado != null) {
            printProcesoDetalle(encontrado);
        } else {
            System.out.println("No existe proceso con PID=" + pid + " en memoria");
        }
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


    // Recorre e imprime todos los eventos de un proceso con sus instrucciones
   // Se reutiliza en printStatusVerbose y en printProcesoDetalle
    private void printEventosDelProceso(Proceso p) {
        MyLinkedListImpl<Evento> eventos = p.getEventosAsociados();
        for (int i = 0; i < eventos.size(); i++) {
            Evento ev = eventos.get(i);
            StringBuilder sb = new StringBuilder();
            sb.append("\t  EVENT: ").append(ev.getTipo()).append(" | Instructions [");
            MyLinkedListImpl<String> instrucciones = ev.getInstrucciones();
            for (int j = 0; j < instrucciones.size(); j++) {
                sb.append(instrucciones.get(j));
                if (j < instrucciones.size() - 1) sb.append(", ");
            }
            sb.append("]");
            System.out.println(sb.toString());
        }
    }


    // Muestra cabecera del proceso mas todos sus eventos
// Se reutiliza en printStatusByProcess
    private void printProcesoDetalle(Proceso p) {
        System.out.println("\tPID=" + p.getPid()
                + " | " + p.getNombre()
                + " | USER:" + p.getUsuarioPropietario().getAlias()
                + " UID:" + p.getUsuarioPropietario().getUid()
                + " | STATE:" + p.getEstado()
                + " | P=" + p.getPrioridad());
        printEventosDelProceso(p);
    }
}
