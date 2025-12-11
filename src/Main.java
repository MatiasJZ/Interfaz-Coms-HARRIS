import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class Main extends JFrame {

    private static final long serialVersionUID = 1L;

    private JTextArea area;
    private JTextField txtIP, txtPuerto;

    private JComboBox<String> comboInterfaces;         // Lista de interfaces
    private InetAddress[] listaIPs;                    // IPs reales asociadas

    private ServerSocket server;
    private boolean servidorActivo = false;

    public Main() {
        super("Prueba Harris - Modo IP (TCP)");
        setSize(620, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);

        //------------------------------------
        // Panel Superior de configuración
        //------------------------------------
        JPanel top = new JPanel(new GridLayout(2, 3, 5, 5));
        top.setBorder(BorderFactory.createTitledBorder("Configuración IP"));

        txtIP = new JTextField("192.168.105.1");   // IP destino
        txtPuerto = new JTextField("5056");        // Puerto TCP

        JButton btnEnviar = new JButton("Enviar");
        JButton btnServidor = new JButton("Iniciar Servidor");

        top.add(new JLabel("IP destino:"));
        top.add(txtIP);
        top.add(btnEnviar);

        top.add(new JLabel("Puerto:"));
        top.add(txtPuerto);
        top.add(btnServidor);

        add(top, BorderLayout.NORTH);

        //------------------------------------
        // Selector de interfaz local
        //------------------------------------
        comboInterfaces = new JComboBox<>();
        listaIPs = cargarInterfaces();

        JPanel panelInterfaces = new JPanel(new BorderLayout());
        panelInterfaces.setBorder(BorderFactory.createTitledBorder("Seleccionar interfaz local"));
        panelInterfaces.add(comboInterfaces, BorderLayout.CENTER);

        add(panelInterfaces, BorderLayout.SOUTH);

        //------------------------------------
        // Consola de logs
        //------------------------------------
        area = new JTextArea();
        area.setEditable(false);
        area.setBackground(Color.BLACK);
        area.setForeground(Color.GREEN);
        area.setFont(new Font("Consolas", Font.PLAIN, 14));

        add(new JScrollPane(area), BorderLayout.CENTER);

        //------------------------------------
        // Acciones
        //------------------------------------
        btnEnviar.addActionListener(e -> enviarMensaje());
        btnServidor.addActionListener(e -> iniciarServidor());
    }

    // =====================================================================
    //  Cargar interfaces de red disponibles
    // =====================================================================
    private InetAddress[] cargarInterfaces() {

        List<InetAddress> ips = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();

            while (nets.hasMoreElements()) {
                NetworkInterface ni = nets.nextElement();

                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual())
                    continue;

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

    // =====================================================================
    //  Iniciar Servidor TCP en la interfaz seleccionada
    // =====================================================================
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

                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(cliente.getInputStream()));

                    String linea = br.readLine();
                    if (linea != null) {
                        log("[RX] " + linea);
                    }

                    cliente.close();
                }

            } catch (Exception e) {
                log("[ERROR] No se pudo iniciar el servidor: " + e.getMessage());
            }
        }).start();
    }

    // =====================================================================
    //  Enviar mensaje TCP usando la interfaz seleccionada
    // =====================================================================
    private void enviarMensaje() {

        int idx = comboInterfaces.getSelectedIndex();

        if (idx < 0) {
            log("[ERROR] Selecciona una interfaz antes de enviar.");
            return;
        }

        InetAddress ipLocal = listaIPs[idx];

        String ipDestino = txtIP.getText().trim();
        int puerto = Integer.parseInt(txtPuerto.getText().trim());

        String mensaje = JOptionPane.showInputDialog(this, "Mensaje a enviar:", "TX", JOptionPane.PLAIN_MESSAGE);

        if (mensaje == null || mensaje.isEmpty()) {
            log("[INFO] Envío cancelado.");
            return;
        }

        new Thread(() -> {
            try {
                log("[INFO] Enviando desde " + ipLocal.getHostAddress());

                Socket socket = new Socket();

                // Forzamos a usar la IP de la interfaz seleccionada
                socket.bind(new InetSocketAddress(ipLocal, 0));

                socket.connect(new InetSocketAddress(ipDestino, puerto), 3000);

                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
                pw.println(mensaje);

                log("[TX] " + mensaje);

                socket.close();

            } catch (ConnectException e) {
                String msg = e.getMessage();
                if (msg.contains("refused"))
                    log("[ERROR] El servidor remoto NO está escuchando.");
                else if (msg.contains("No route"))
                    log("[ERROR] No hay ruta IP hacia " + ipDestino + " (ver radio o enlace RF).");
                else
                    log("[ERROR] Fallo de conexión: " + msg);

            } catch (SocketTimeoutException e) {
                log("[ERROR] Timeout: el destino no respondió.");

            } catch (Exception e) {
                log("[ERROR] Error inesperado: " + e.getMessage());
            }
        }).start();
    }

    // =====================================================================
    //  Log en pantalla
    // =====================================================================
    private void log(String txt) {
        SwingUtilities.invokeLater(() -> {
            area.append(txt + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    // =====================================================================
    //  MAIN
    // =====================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
