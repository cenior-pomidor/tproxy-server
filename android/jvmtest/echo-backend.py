#!/usr/bin/env python3
"""Stands in for the stock MTProxy backend: echoes bytes, closes on BYE!."""

import socket
import sys
import threading


def handle(connection):
    try:
        while True:
            data = connection.recv(65536)
            if not data:
                break
            connection.sendall(data)
            if b"BYE!" in data:
                break
    except OSError:
        pass
    finally:
        try:
            connection.close()
        except OSError:
            pass


def main():
    port = int(sys.argv[1])
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind(("127.0.0.1", port))
    listener.listen(64)
    print("echo backend on 127.0.0.1:%d" % port, flush=True)
    while True:
        connection, _ = listener.accept()
        connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        threading.Thread(target=handle, args=(connection,), daemon=True).start()


if __name__ == "__main__":
    main()
