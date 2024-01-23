package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;

/*
Clase de conexión, se encarga de la comunicación entre el cliente y el servidor.
*/
public class Connection implements Runnable {
    private Socket socket;
    private String address;
    private Integer PORT = 1234;
    private HealthCareConnection hcc;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private ServerConnector serverConnector;
    private ClientConnector clientConnector;
    private Main controller;

    private long timeReceivedLastMessage;
    private volatile boolean runState = true;

    private boolean wasClient = false;

    public Connection(String address, Main controller) {
        this.controller = controller;
        this.address = address;
        createConnection();
    }

    //CREACION DE CONEXIÓN GENERAL ENTRE CLIENTE Y SERVIDOR
    private void createConnection() {
        try {

            this.clientConnector = new ClientConnector(PORT, address);
            clientConnector.setIntentarReconectar(false);
            clientConnector.run();
            this.socket = clientConnector.getSOCKET();
            boolean conexionEstablecida = clientConnector.isConexionEstablecida();
            if (conexionEstablecida) {
                wasClient = true;
                System.out.println("Conexion establecida como cliente correctamente");
            } else {
                clientConnector.setIntentarReconectar(true);
                System.out.println("Abortando conexion como cliente...");
                // Iniciar el servidor
                this.serverConnector = new ServerConnector(PORT);
                //serverConnector.run();
                this.socket = serverConnector.getClsock();
                System.out.println("Conexion establecida como servidor correctamente");
            }
            System.out.println(socket);
            // Inicializar los objetos BufferedReader y PrintWriter
            OutputStream os = socket.getOutputStream();
            this.outputStream = new ObjectOutputStream(os);
            InputStream is = socket.getInputStream();
            this.inputStream = new ObjectInputStream(is);


        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            throw new RuntimeException(ex);
        }

    }

    public void reconnect() {
        try {
            PORT = 1234;
            // Iniciar servidor y esperar conexiones entrantes
            System.out.println("Reiniciando servidor...");
            serverConnector = new ServerConnector(PORT);
            //serverConnector.run();
            socket = serverConnector.getClsock();
            HealthCareConnection healthCareConnection = new HealthCareConnection(this, 10000);
            setHCC(healthCareConnection);
            new Thread(healthCareConnection).start();

            // Reinicializar los objetos BufferedReader y PrintWriter
            this.inputStream = new ObjectInputStream(socket.getInputStream());
            this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException ex) {
            System.err.println("No se ha podido conectar");
        } catch (NullPointerException e) {
            System.out.println("Error en la conexión: " + e);
        }
    }

    public void enviarMensanje(Message message) {
        try {
            message.sender = (wasClient ? "Cliente" : "Servidor");
            outputStream.writeObject(message);
            outputStream.flush();
        } catch (Exception e) {
            System.out.println(
                    "No se puede enviar el mensaje, no se ha establecido la conexión" + "\n" + "Codigo de error: " + e);
        }
    }

    public void setHCC(HealthCareConnection hch) {
        this.hcc = hch;
    }

    public void mssgIn() {
        Message m;
        System.out.println("Esperando mensaje...");
        try {
            while (socket != null && socket.isConnected() && (m = (Message) inputStream.readObject()) != null) {
                System.out.println("Recibido ping");
                if (m.isPing()) {
                    System.out.println("Recibido ping");
                    mssgIn();
                    return;
                }
                controller.recibirMensaje(m);
                long time = (System.currentTimeMillis());
                setTimeReceivedLastMessage(time);
            }
        } catch (Exception e) {
            System.err.println("Error en la conexion");
            killSocket();
        }
    }

    public void setTimeReceivedLastMessage(long timeReceivedLastMessage) {
        this.timeReceivedLastMessage = timeReceivedLastMessage;
    }

    public boolean ping() {
        try {
            System.out.println("entra en ping");
            outputStream.writeObject(Message.ping());
            return true;
        } catch (Exception e) {
            System.out.println("Error en el envío del heartbeat: " + e);
            return false;
        }
    }

    public void run() {
        while (runState) {
            System.out.println("no se ha perdido la conexion");
            try {
                // Verificar si la conexión actual está cerrada
                if (socket == null || socket.isClosed() || !socket.isConnected()) {
                    System.out.println("Conexión perdida, intentando reconectar...");
                    Thread.sleep(1000);
                    reconnect();
                }

                // Leer mensajes entrantes
                if (socket != null && socket.isConnected()) {
                    mssgIn();
                }

                // Esperar un poco antes de verificar la conexión de nuevo
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("Error en la reconexión: " + e);
            }
        }
    }

    public long getTimeReceivedLastMessage() {
        return timeReceivedLastMessage;
    }

    public synchronized void killSocket() {
        try {
            stopHCC();
            inputStream.close();
            outputStream.close();
            if (serverConnector != null) {
                socket.close();
                if (!serverConnector.isSocketClosed()) {
                    serverConnector.killSocket();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket = null;
            PORT = null;
            System.err.println("Matando el socket...");
        }

    }

    public void stopHCC() {
        if (hcc != null) {
            hcc.pararEjecucion();
            hcc = null;
        }
    }

    @Override
    public String toString() {
        return "esto existe";
    }

}