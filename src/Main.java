/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 *
 * @author dii
 */
public class Main {
    private static final int MENSAJE = 1;
    private static final int ARCHIVO = 2;
    private static final int LISTA_ARCHIVOS = 3;
    private static final int SOLICITAR_ARCHIVOS = 4;
    private static final int ARCHIVO_CIFRADO = 5;
    private static final int LISTA_ARCHIVOS_CIFRADOS = 6;

    public static void main(String[] args) {
        int PORT = 4242;
        String folderOrigen = "." + "\\origen";
        String folderDestino = "." + "\\destino";
        String folderCifrados = "." + "\\libros_cifrados";
        if (args.length > 0) {
            PORT = Integer.parseInt(args[0]);
        }
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            //Conexión
            Socket socket = serverSocket.accept();
            DataInputStream din = new DataInputStream(socket.getInputStream());
            DataOutputStream dout=new DataOutputStream(socket.getOutputStream());
            Scanner scanner = new Scanner(System.in);

            System.out.println("Servidor iniciado...");
            System.out.printf("Cliente " + socket.getInetAddress().getHostAddress() + " se ha conectado\n");
            //Recepción
            Thread receiveThread = new Thread(()-> {
                try {
                    while (!socket.isClosed()) {
                        int tipo = din.readInt();
                        switch (tipo) {
                            case MENSAJE:
                                String message = din.readUTF();
                                System.out.println("Cliente: "+message);
                                break;
                            case ARCHIVO:
                                recibirArchivo(din, folderDestino);
                                break;
                            case LISTA_ARCHIVOS:
                                recibirListaArchivos(din);
                                break;
                            case SOLICITAR_ARCHIVOS:
                                dout.writeInt(LISTA_ARCHIVOS);
                                List<String> lista = obtenlistaArchivosLocales(folderOrigen);
                                enviarListaArchivos(dout, lista);
                                break;
                            case ARCHIVO_CIFRADO:
                                dout.writeInt(ARCHIVO);
                                descifrarArchivo();
                            default:
                                System.out.println("Tipo:"+tipo);
                                break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("El cliente se desconecto...");
                }
            });
            receiveThread.start();

//          Envío
            while (true) {
                try {
                    String clientMessage = scanner.nextLine();
                    String command = clientMessage.split(" ")[0];

                    switch (command) {
                        case "/archivo":
                            String filePath = clientMessage.substring(8).trim();
                            filePath = folderOrigen + filePath;
                            enviarArchivo(dout, filePath);
                            break;

                        case "/lista_local":
                            List<String> listaArchivos = obtenlistaArchivosLocales(".");
                            muestraLista(listaArchivos);
                            break;

                        case "/lista_remota":
                            dout.writeInt(SOLICITAR_ARCHIVOS);
                            break;

                        case "/help":
                            System.out.println("Lista de comandos: \n" +
                                    "/archivo <ruta>    \tEnvia un archivo al dispositivo con conexion\n" +
                                    "/lista_local       \tMuestra lista de archivos locales\n" +
                                    "/lista_remota      \tMuestra lista de archivos del dispositivo con conexion");
                            break;

                        default:
                            dout.writeInt(MENSAJE);
                            dout.writeUTF(clientMessage);
                            dout.flush();
                            break;
                    }
                } catch (IOException e) {
                    System.out.println("Error al enviar mensaje: " + e.getMessage());
                    if (socket.isClosed()) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error de conexion:"+e.getMessage());
        }
    }
    private static void recibirArchivo(DataInputStream din, String folderDestino) throws Exception {
        // Leemos longitud de archivo
        int fileNameLength = din.readInt();

        // Read filename
        byte[] fileNameBytes = new byte[fileNameLength];
        din.readFully(fileNameBytes, 0, fileNameLength);
        String fileName = new String(fileNameBytes);

        // Read file length
        long fileLength = din.readLong();

        System.out.println("Recibiendo archivo: " + fileName + " (" + fileLength + " bytes)");

        // Create output file
        File file = new File(folderDestino, "received_" + fileName);
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            // Read file contents
            byte[] buffer = new byte[4 * 1024];
            int bytes;
            long totalBytesRead = 0;

            while (totalBytesRead < fileLength) {
                bytes = din.read(buffer, 0,
                        (int) Math.min(buffer.length, fileLength - totalBytesRead));
                fileOutputStream.write(buffer, 0, bytes);
                totalBytesRead += bytes;
            }
        }
        System.out.println("Archivo " + fileName + " recibido exitosamente");
    }

    private static void enviarArchivo(DataOutputStream dout, String path) throws Exception {
        File file = new File(path);
        FileInputStream fileInputStream = new FileInputStream(file);

        // Send file type
        dout.writeInt(ARCHIVO);

        // Send filename first
        String filename = file.getName();
        dout.writeInt(filename.length());
        dout.write(filename.getBytes());

        // Send file length
        dout.writeLong(file.length());

        // Send file contents
        byte[] buffer = new byte[4 * 1024];
        int bytes;
        while ((bytes = fileInputStream.read(buffer)) != -1) {
            dout.write(buffer, 0, bytes);
        }

        fileInputStream.close();
        dout.flush();
        System.out.println("Archivo " + filename + " enviado exitosamente");
    }
    public static List<String> obtenlistaArchivosLocales(String path) {
        File carpeta = new File(path);
        File[] archivos = carpeta.listFiles();
        List<String> lista = new ArrayList<>();
        for (File archivo : archivos) {
            if (archivo.isDirectory()) {
                lista.add("[Carpeta] " + archivo.getName());
            } else if (archivo.isFile()) {
                lista.add("[Archivo] " + archivo.getName());
            }
        }
        return lista;
    }
    public static void muestraLista(List<String> lista) {
        for (String string : lista) {
            System.out.println("  "+string);
        }
    }
    public static void enviarListaArchivos(DataOutputStream dout, List<String> lista) {
        try {
            for (String archivo : lista) {
                dout.writeUTF(archivo);
            }
            dout.writeUTF(""); // Marcador de fin
            dout.flush();
            System.out.println("Lista de archivos enviada.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void recibirListaArchivos(DataInputStream din) {
        List<String> lista = new ArrayList<>();
        try {
            System.out.println("Recibiendo lista de archivos...");
            while (true) {
                String archivo = din.readUTF();
                if (archivo == null || archivo.isEmpty()) {
                    break;
                }
                lista.add(archivo);
            }
            muestraLista(lista);
        } catch (IOException e) {
            System.out.println("Error al recibir la lista de archivos: " + e.getMessage());
        }
    }

    public static void descifrarArchivo(){}
    public static void descifrarListaArchivos(){}
}
