import os
import socket
import struct
import threading
import time
import sys
PACKET_SIZE = 1024
HEADER_SIZE = 2
MAX_DATA_SIZE = PACKET_SIZE - HEADER_SIZE

def create_packet(seq_number, data):
    header = struct.pack('>H', seq_number)
    return header + data


def send_data_thread(sock, receiver_address, file, window_size, timeout, ack_received, seq_number):
    packets_in_flight = []

    while True:
        with ack_lock:
            while ack_received and seq_number - ack_received[0] < window_size:
                data_chunk = file.read(MAX_DATA_SIZE)
                print("Data to be sent:", len(data_chunk), "bytes")

                if not data_chunk:
                    break

                packet = create_packet(seq_number, data_chunk)
                sock.sendto(packet, receiver_address)
                print("Packet sent:", seq_number)

                packets_in_flight.append(seq_number)
                seq_number += 1

        with ack_lock:
            for ack_number in ack_received:
                if ack_number in packets_in_flight:
                    packets_in_flight.remove(ack_number)

        if not packets_in_flight:
            break

        time.sleep(timeout)

def listen_ack_thread(sock, ack_received, end_seq_number):
    while True:
        try:
            ack_packet, _ = sock.recvfrom(PACKET_SIZE)
            ack_number = struct.unpack('>H', ack_packet[:HEADER_SIZE])[0]
        except OSError:
            break

        with ack_lock:
            ack_received.append(ack_number)
            if ack_received and ack_received[0] == end_seq_number:
                break

file_path = sys.argv[1]
receiver_port = int(sys.argv[2])
window_size = int(sys.argv[3])
timeout = float(sys.argv[4]) / 1000
print(file_path, receiver_port, window_size, timeout)

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
receiver_address = ('localhost', receiver_port)

file = open(file_path, 'rb')
file_size = os.path.getsize(file_path)
print("Image file size:", file_size, "bytes")

ack_received = []
ack_lock = threading.Lock()
send_thread = threading.Thread(target=send_data_thread, args=(sock, receiver_address, file, window_size, timeout, ack_received, 1))
listen_thread = threading.Thread(target=listen_ack_thread, args=(sock, ack_received, 1))

send_thread.start()
listen_thread.start()

send_thread.join()

end_packet = create_packet(0, b'')
sock.sendto(end_packet, receiver_address)

listen_thread.join()

sock.close()
file.close()

print(ack_received)

