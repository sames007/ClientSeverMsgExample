# ClientServerMsgExample

A lightweight JavaFX app that lets you:
- Check if a host is listening on common ports  
- Spin up a simple server/chat UI  
- Connect a client to the server for two-way messaging  

## Getting Started

1. Clone or download this repo.  
2. Make sure JavaFX is on your classpath (or your IDE’s library settings).  
3. Run the `MainController` as a JavaFX application.

## Features

- **Port Checker**: Type a hostname, pick a port, and verify connectivity.  
- **Server UI**: Launch a server window on port 6666 to chat with one client.  
- **Client UI**: Connect to `localhost:6666` and exchange messages until someone types “exit.”

## How to Use

- **Check Connection**:  
  1. Enter a host in the text field.  
  2. Select a port from the dropdown.  
  3. Click **Check Connection**.  
- **Start Server**: Click **Start Server** to open the chat window, then wait for a client.  
- **Start Client**: Click **Start Client**, then hit **Connect** to join the chat.  

Type messages and hit **Send** (or type “exit” to close).
