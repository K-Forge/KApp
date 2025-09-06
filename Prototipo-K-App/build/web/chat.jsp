<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Chat Universitario</title>
    <link rel="stylesheet" type="text/css" href="css/style.css">
</head>
<body>
    <div class="main-container">
        <div class="sidebar">
            <h3>Chats disponibles</h3>
            <ul id="chat-list">
                <li onclick="selectChat('Chat con Juan')">Chat con Juan</li>
                <li onclick="selectChat('Chat con María')">Chat con María</li>
            </ul>
        </div>
        <div class="chat-area">
            <div class="chat-header">
                <h2 id="chat-title">No se ha seleccionado ningún chat</h2>
            </div>
            <div class="chat-box" id="chat-box">
                <!-- Mensajes aparecerán aquí -->
            </div>
            <div class="chat-input">
                <input type="text" id="message" placeholder="Escribe un mensaje...">
                <button onclick="sendMessage()">Enviar</button>
            </div>
        </div>
    </div>
    <script src="js/chat.js"></script>
</body>
</html>
