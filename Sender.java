public class Sender
{
   static final int NUM_OF_ARGS = 4;
   public static void main(String[] args)
   {
      if (args.length != NUM_OF_ARGS)
      {
         System.out.println("Usage: java Sender <file_path> <receiver_port> <window_size_N> <retransmission_timeout>");
         System.exit(1);
      }
      String file_path = args[0];
      int receiver_port = Integer.parseInt(args[1]);
      int window_size_N = Integer.parseInt(args[2]);
      int retransmission_timeout = Integer.parseInt(args[3]);
   }
}