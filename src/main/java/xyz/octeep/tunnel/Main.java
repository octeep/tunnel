package xyz.octeep.tunnel;

import picocli.CommandLine;

@CommandLine.Command(name = "tunnel")
public class Main implements Runnable {

    @Override
    public void run() {
        if(mainGroup.clientArgGroup != null) {
            Start.startClient(mainGroup.clientArgGroup);
        } else if(mainGroup.serverArgGroup != null) {
            Start.startServer(mainGroup.serverArgGroup);
        }
    }

    @CommandLine.Option(names = {"-?", "--help"}, usageHelp = true, description = "Display this help and exit.")
    private boolean help;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    public static MainGroup mainGroup;

    static class MainGroup {
        @CommandLine.ArgGroup(exclusive = false, multiplicity = "0..1", heading = "Start tunnel as a server:\n")
        ServerArgGroup serverArgGroup;

        @CommandLine.ArgGroup(exclusive = false, multiplicity = "0..1", heading = "Start tunnel as a client:\n")
        ClientArgGroup clientArgGroup;

        static class ServerArgGroup {
            @CommandLine.Option(names = {"-l", "--listen"}, description = "Start tunnel in listening mode.", required = true)
            boolean ignored;
            @CommandLine.Option(names = {"-E", "--encrypt"}, description = "Enable encryption (AES-128-GCM).")
            boolean enableEncryption;
            @CommandLine.Option(names = {"-k", "--secret-key"}, description = "Base64 encoded 32-byte secret key.", required = true)
            String secretKey;
            @CommandLine.Option(names = {"-e", "--endpoint"}, description = "ip:port where the TCP traffic shall be proxied to.", required = true)
            String endpoint;
        }

        static class ClientArgGroup {
            @CommandLine.Option(names = {"-c", "--client"}, description = "Start tunnel in client mode.", required = true)
            boolean ignored;
            @CommandLine.Option(names = {"-s", "--server-id"}, description = "Server ID of the remote listening tunnel.", required = true)
            String serverID;
            @CommandLine.Option(names = {"-p", "--port"}, description = "Local port to bind the proxy to.", required = true)
            int port;
        }
    }

    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }

}
