function sendMessage() {
    var messageBox = document.getElementById("message");
    var chatBox = document.getElementById("chat-box");
    var message = messageBox.value;
    if (message.trim() !== "") {
        var newMessage = document.createElement("div");
        newMessage.textContent = message;
        chatBox.appendChild(newMessage);
        messageBox.value = "";
        chatBox.scrollTop = chatBox.scrollHeight;
    }
}

function selectChat(chatName) {
    document.getElementById("chat-title").textContent = chatName;
    var chatBox = document.getElementById("chat-box");
    chatBox.innerHTML = "";
    var welcome = document.createElement("div");
    welcome.textContent = "Bienvenido al " + chatName;
    chatBox.appendChild(welcome);
}
