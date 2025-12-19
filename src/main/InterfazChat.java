package main;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;

public class InterfazChat extends JFrame {

    private static final long serialVersionUID = 1L;

    private JTextArea area;
    private JTextField txtIP, txtPuerto;

    private JComboBox<String> comboInterfaces;  // Lista de interfaces
    private InetAddress[] listaIPs;              // IPs reales asociadas
    private ServerSocket server;
    private boolean servidorActivo = false;

    public InterfazChat() {
        super("Chat Harris - IP (TCP)");
        setSize(740, 580);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setLocationRelativeTo(null);

        JPanel top = new JPanel(new GridLayout(2, 4, 6, 6));
        top.setBorder(BorderFactory.createTitledBorder("Configuración IP"));

        txtIP = new JTextField("192.168.105.1");
        txtPuerto = new JTextField("5056");

        JButton btnEnviarTexto = new JButton("Enviar TXT");
        JButton btnEnviarArchivo = new JButton("Enviar Archivo");
        JButton btnServidor = new JButton("Iniciar Servidor");

        top.add(new JLabel("IP destino:"));
        top.add(txtIP);
        top.add(btnEnviarTexto);
        top.add(btnEnviarArchivo);

        top.add(new JLabel("Puerto:"));
        top.add(txtPuerto);
        top.add(btnServidor);
        top.add(new JLabel(""));

        add(top, BorderLayout.NORTH);

        comboInterfaces = new JComboBox<>();
        listaIPs = cargarInterfaces();

        JPanel panelInterfaces = new JPanel(new BorderLayout());
        panelInterfaces.setBorder(BorderFactory.createTitledBorder("Seleccionar interfaz local"));
        panelInterfaces.add(comboInterfaces, BorderLayout.CENTER);
        add(panelInterfaces, BorderLayout.SOUTH);

        area = new JTextArea();
        area.setEditable(false);
        area.setBackground(Color.BLACK);
        area.setForeground(Color.GREEN);
        area.setFont(new Font("Consolas", Font.PLAIN, 14));
        add(new JScrollPane(area), BorderLayout.CENTER);

        btnEnviarTexto.addActionListener(e -> enviarMensajeTexto());
        btnEnviarArchivo.addActionListener(e -> enviarArchivo());
        btnServidor.addActionListener(e -> iniciarServidor());
    }

    private InetAddress[] cargarInterfaces() {
        ArrayList<InetAddress> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface ni = nets.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (addr instanceof Inet4Address) {
                        comboInterfaces.addItem(ni.getDisplayName() + " - " + addr.getHostAddress());
                        ips.add(addr);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ips.toArray(new InetAddress[0]);
    }

    private void iniciarServidor() {
        if (servidorActivo) {
            log("[SERVIDOR] Ya está iniciado.");
            return;
        }

        int puerto = Integer.parseInt(txtPuerto.getText().trim());
        int idx = comboInterfaces.getSelectedIndex();
        if (idx < 0) {
            log("[ERROR] Ninguna interfaz seleccionada.");
            return;
        }
        InetAddress ipLocal = listaIPs[idx];

        new Thread(() -> {
            try {
                server = new ServerSocket(puerto, 50, ipLocal);
                servidorActivo = true;
                log("[SERVIDOR] Escuchando en " + ipLocal.getHostAddress() + ":" + puerto);

                while (servidorActivo) {
                    Socket cliente = server.accept();
                    manejarCliente(cliente);
                }
            } catch (Exception e) {
                log("[ERROR] No se pudo iniciar el servidor: " + e.getMessage());
            }
        }).start();
    }

    private void manejarCliente(Socket cliente) {
        new Thread(() -> {
            try (DataInputStream dis = new DataInputStream(cliente.getInputStream())) {
                String tipo = dis.readUTF(); // TEXT | FILE

                if ("TEXT".equals(tipo)) {
                    String linea = dis.readUTF();
                    log("[RX-TXT] " + linea);

                } else if ("FILE".equals(tipo)) {
                    String nombre = dis.readUTF();
                    long size = dis.readLong();

                    File dir = new File("recibidos");
                    dir.mkdirs();
                    File out = new File(dir, nombre);

                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[4096];
                        long recibidos = 0;
                        while (recibidos < size) {
                            int r = dis.read(buf, 0, (int) Math.min(buf.length, size - recibidos));
                            if (r == -1) break;
                            fos.write(buf, 0, r);
                            recibidos += r;
                        }
                    }

                    log("[RX-ARCHIVO] Recibido: " + out.getName() + " (" + size + " bytes)");
                    abrirArchivo(out);
                }
            } catch (Exception e) {
                log("[ERROR RX] " + e.getMessage());
            } finally {
                try { cliente.close(); } catch (IOException ignored) {}
            }
        }).start();
    }

    private void abrirArchivo(File f) {
        try {
            if (!Desktop.isDesktopSupported()) {
                log("[INFO] Apertura automática no soportada.");
                return;
            }
            Desktop.getDesktop().open(f);
            log("[INFO] Archivo abierto: " + f.getName());
        } catch (Exception e) {
            log("[ERROR] No se pudo abrir el archivo: " + e.getMessage());
        }
    }

    private void enviarMensajeTexto() {
        int idx = comboInterfaces.getSelectedIndex();
        if (idx < 0) {
            log("[ERROR] Selecciona una interfaz antes de enviar.");
            return;
        }
        InetAddress ipLocal = listaIPs[idx];
        String ipDestino = txtIP.getText().trim();
        int puerto = Integer.parseInt(txtPuerto.getText().trim());

        String mensaje = JOptionPane.showInputDialog(this, "Mensaje TXT a enviar:", "TX", JOptionPane.PLAIN_MESSAGE);
        if (mensaje == null || mensaje.isEmpty()) {
            log("[INFO] Envío cancelado.");
            return;
        }

        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.bind(new InetSocketAddress(ipLocal, 0));
                socket.connect(new InetSocketAddress(ipDestino, puerto), 3000);

                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF("TEXT");
                dos.writeUTF(mensaje);
                dos.flush();

                log("[TX-TXT] " + mensaje);
            } catch (Exception e) {
                log("[ERROR TX] " + e.getMessage());
            }
        }).start();
    }

    private void enviarArchivo() {
        int idx = comboInterfaces.getSelectedIndex();
        if (idx < 0) {
            log("[ERROR] Selecciona una interfaz antes de enviar.");
            return;
        }
        InetAddress ipLocal = listaIPs[idx];
        String ipDestino = txtIP.getText().trim();
        int puerto = Integer.parseInt(txtPuerto.getText().trim());

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Seleccionar archivo KML o TXT");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File f = fc.getSelectedFile();
        String name = f.getName().toLowerCase();
        if (!(name.endsWith(".kml") || name.endsWith(".txt") || name.endsWith(".pdf"))) {
            JOptionPane.showMessageDialog(this, "Solo se permiten .kml, .txt o .pdf");
            return;
        }

        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.bind(new InetSocketAddress(ipLocal, 0));
                socket.connect(new InetSocketAddress(ipDestino, puerto), 3000);

                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF("FILE");
                dos.writeUTF(f.getName());
                dos.writeLong(f.length());

                Files.copy(f.toPath(), dos);
                dos.flush();

                log("[TX-FILE] Enviado: " + f.getName() + " (" + f.length() + " bytes)");
            } catch (Exception e) {
                log("[ERROR TX] " + e.getMessage());
            }
        }).start();
    }

    private void log(String txt) {
        SwingUtilities.invokeLater(() -> {
            area.append(txt + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new InterfazChat().setVisible(true));
    }
}
	