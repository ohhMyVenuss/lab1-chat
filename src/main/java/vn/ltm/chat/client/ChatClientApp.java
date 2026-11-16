package vn.ltm.chat.client;

import vn.ltm.common.ChatWire;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ChatClientApp extends Application {
    private final TextField host = new TextField(), username = new TextField(System.getenv().getOrDefault("CHAT_USERNAME", "Client01"));
    private final TextField message = new TextField(), groupName = new TextField("team-a");
    private final TextField tcpPort = new TextField("5000"), udpPort = new TextField("5001");
    private final ComboBox<String> mode = new ComboBox<>();
    private final ComboBox<String> recipient = new ComboBox<>();
    private final ListView<String> online = new ListView<>();
    private final VBox messages = new VBox(12);
    private final ScrollPane messageScroll = new ScrollPane(messages);
    private final Label connection = new Label("Chưa kết nối"), roomTitle = new Label("Phòng trò chuyện chung"), accountName = new Label("●  Chưa đăng nhập");
    private final Label onlineCount = new Label("0 ONLINE");
    private TcpChatClient tcp; private UdpChatClient udp; private String me="";
    private String multicastGroup="239.10.10.10"; private int multicastPort=5002;

    @Override public void start(Stage stage) {
        loadConfig();
        BorderPane root=new BorderPane(); root.setPadding(new Insets(18));
        VBox shell=new VBox(14); root.setCenter(shell);
        HBox top=topbar(); shell.getChildren().add(top);
        HBox body=new HBox(14); VBox.setVgrow(body,Priority.ALWAYS); shell.getChildren().add(body);
        VBox sidebar=sidebar(); sidebar.setPrefWidth(218); body.getChildren().add(sidebar);
        VBox chat=chatPanel(); HBox.setHgrow(chat,Priority.ALWAYS); body.getChildren().add(chat);
        VBox details=detailsPanel(); details.setPrefWidth(230); body.getChildren().add(details);
        Scene scene=new Scene(root,1360,850); scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        stage.setTitle("LTM Connect · Lab 01 — Network Chat"); stage.setMinWidth(1120); stage.setMinHeight(740); stage.setScene(scene);
        stage.setOnCloseRequest(e->disconnect()); stage.show();
        addMessage("Hệ thống","Chào mừng đến LTM Connect. Kết nối tới Server bằng IP LAN để bắt đầu.",false,"SYSTEM");
    }
    private HBox topbar(){
        VBox brand=new VBox(2,new Label("LTM CONNECT"),new Label("NETWORK CHAT · LAB 01")); brand.getChildren().get(0).getStyleClass().add("title"); brand.getChildren().get(1).getStyleClass().add("subtitle");
        host.setPromptText("IP LAN của máy Server"); host.setPrefWidth(180); username.setPrefWidth(120); tcpPort.setPrefWidth(72);
        Button connect=new Button("Kết nối Server"); connect.getStyleClass().add("button-primary"); connect.setOnAction(e->connect());
        HBox fields=new HBox(8,new Label("SERVER"),host,new Label("PORT"),tcpPort,new Label("TÊN HIỂN THỊ"),username,connect); fields.setAlignment(Pos.CENTER_RIGHT); HBox.setHgrow(host,Priority.NEVER);
        HBox bar=new HBox(16,brand,new Region(),fields); HBox.setHgrow(bar.getChildren().get(1),Priority.ALWAYS); bar.setAlignment(Pos.CENTER_LEFT); bar.getStyleClass().add("topbar"); return bar;
    }
    private VBox sidebar(){
        VBox box=new VBox(13); box.getStyleClass().add("sidebar"); box.setPadding(new Insets(18));
        Label brand=new Label("●  LTM CONNECT"); brand.getStyleClass().add("brand"); Label cap=new Label("WORKSPACE"); cap.getStyleClass().add("brand-small");
        Label all=nav("▣   Tất cả tin nhắn",true), groups=nav("◉   Nhóm của tôi",false), direct=nav("✉   Tin nhắn riêng",false), network=nav("⌁   Kênh UDP",false);
        Label spaces=new Label("KHÔNG GIAN CHAT"); spaces.getStyleClass().add("sidebar-title");
        Label general=nav("#   general",true), study=nav("#   study-group",false), project=nav("#   project-team",false);
        Region spacer=new Region(); VBox.setVgrow(spacer,Priority.ALWAYS);
        Label foot=new Label("LTM NETWORK LABS\nBuilt for LAN demonstration"); foot.getStyleClass().add("brand-small");
        box.getChildren().addAll(brand,cap,all,groups,direct,network,new Separator(),spaces,general,study,project,spacer,foot); return box;
    }
    private Label nav(String text,boolean active){Label b=new Label(text); b.setMaxWidth(Double.MAX_VALUE); b.setPadding(new Insets(11,13,11,13)); b.getStyleClass().add("nav-item"); if(active)b.getStyleClass().add("nav-active"); return b;}
    private VBox chatPanel(){
        VBox panel=new VBox(12); panel.getStyleClass().add("card");
        HBox head=new HBox(12); head.setAlignment(Pos.CENTER_LEFT);
        VBox titles=new VBox(3,roomTitle,new Label("Trao đổi thông tin trong mạng nội bộ")); roomTitle.setStyle("-fx-font-size:18px;-fx-font-weight:800;"); titles.getChildren().get(1).getStyleClass().add("subtitle");
        Region spacer=new Region(); HBox.setHgrow(spacer,Priority.ALWAYS); onlineCount.getStyleClass().add("badge"); head.getChildren().addAll(titles,spacer,onlineCount);
        messageScroll.setFitToWidth(true); messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); messageScroll.getStyleClass().add("edge-to-edge"); VBox.setVgrow(messageScroll,Priority.ALWAYS); messages.setPadding(new Insets(8)); messages.setFillWidth(true);
        HBox tools=new HBox(8); mode.getItems().setAll("Broadcast · TCP","Private · TCP","Group · TCP","Broadcast · UDP","Multicast · UDP"); mode.getSelectionModel().selectFirst(); mode.setPrefWidth(175);
        recipient.setPromptText("Người nhận"); recipient.setPrefWidth(145); groupName.setPromptText("Tên nhóm"); groupName.setPrefWidth(115);
        message.setPromptText("Viết tin nhắn của bạn…"); HBox.setHgrow(message,Priority.ALWAYS); message.setOnAction(e->send());
        Button join=new Button("Tham gia nhóm"); join.setOnAction(e->joinGroup());
        tools.getChildren().addAll(mode,recipient,groupName,join);
        Button emoji=new Button("☺ Emoji ▾");
        ContextMenu emojiPicker=createEmojiPicker();
        emoji.setOnAction(e->emojiPicker.show(emoji, javafx.geometry.Side.TOP, 0, 0));
        Button attach=new Button("📎 Đính kèm"); attach.setOnAction(e->sendAttachment());
        Button send=new Button("Gửi tin nhắn  ➤"); send.getStyleClass().add("button-primary"); send.setOnAction(e->send());
        HBox composer=new HBox(8,message,attach,emoji,send); composer.setAlignment(Pos.CENTER);
        Label hint=new Label("Enter để gửi  ·  TCP đảm bảo truyền tin tin cậy  ·  UDP dùng cho broadcast/multicast best-effort"); hint.getStyleClass().add("subtitle");
        panel.getChildren().addAll(head,new Separator(),messageScroll,tools,composer,hint); return panel;
    }
    private VBox detailsPanel(){
        VBox panel=new VBox(13); panel.getStyleClass().add("card");
        Label title=new Label("Thành viên trực tuyến"); title.getStyleClass().add("card-title"); online.setPrefHeight(300); online.setPlaceholder(new Label("Chưa có thành viên"));
        Label meLabel=new Label("TÀI KHOẢN CỦA BẠN"); meLabel.getStyleClass().add("subtitle"); accountName.setStyle("-fx-font-weight:700;");
        Label net=new Label("THÔNG TIN MẠNG"); net.getStyleClass().add("subtitle");
        VBox metrics=new VBox(7,stat("TCP","5000 · Reliable"),stat("UDP Broadcast","5001 · Best effort"),stat("IP Multicast","239.10.10.10:5002"));
        Label statusTitle=new Label("TRẠNG THÁI"); statusTitle.getStyleClass().add("subtitle"); connection.setWrapText(true); connection.getStyleClass().add("badge-warning");
        panel.getChildren().addAll(title,online,new Separator(),meLabel,accountName,new Separator(),net,metrics,statusTitle,connection); return panel;
    }
    private HBox stat(String name,String value){Label a=new Label(name),b=new Label(value); b.getStyleClass().add("muted"); Region s=new Region(); HBox.setHgrow(s,Priority.ALWAYS); HBox h=new HBox(a,s,b); return h;}
    private void loadConfig(){Properties p=new Properties(); Path f=Path.of("config","client.properties"); try(var in=Files.newInputStream(f)){p.load(in);host.setText(p.getProperty("server.host",""));tcpPort.setText(p.getProperty("chat.tcp.port","5000"));udpPort.setText(p.getProperty("chat.udp.listen.port","5001"));multicastGroup=p.getProperty("chat.multicast.group",multicastGroup);multicastPort=Integer.parseInt(p.getProperty("chat.multicast.port","5002"));}catch(Exception ex){host.setPromptText("Nhập IP LAN máy Server");}}
    private void connect(){String h=host.getText().trim(),u=username.getText().trim(); if(h.isBlank()||h.equalsIgnoreCase("localhost")||h.equals("127.0.0.1")){alert("Hãy nhập IPv4 LAN thật của máy Server, không dùng localhost.");return;} if(!u.matches("[A-Za-z0-9_-]{2,32}")){alert("Tên người dùng cần 2–32 ký tự: chữ, số, _ hoặc -.");return;}
        try{disconnect();me=u;tcp=new TcpChatClient(this::onTcpFrame,this::setStatus);tcp.connect(h,Integer.parseInt(tcpPort.getText().trim()),u);try{udp=new UdpChatClient(s->Platform.runLater(()->addMessage("UDP",s,false,"UDP")));udp.start(Integer.parseInt(udpPort.getText().trim()),multicastGroup,multicastPort);}catch(Exception udpError){udp=null;addMessage("UDP chưa sẵn sàng",udpError.getMessage()+" · TCP chat vẫn hoạt động.",false,"WARNING");}accountName.setText("●  "+me);setStatus("Đang kết nối tới "+h);}
        catch(Exception e){disconnect();setStatus("Kết nối thất bại: "+e.getMessage());alert("Không thể kết nối: "+e.getMessage());}}
    private void onTcpFrame(String frame){String[] p=ChatWire.split(frame);Platform.runLater(()->{switch(p[0]){case "HELLO"->addMessage("Server",ChatWire.field(p,1),false,"SYSTEM");case "LOGIN_OK"->{setStatus("Đã kết nối · "+me);addMessage("Hệ thống","Đăng nhập thành công. Bạn đã tham gia phòng chat.",false,"SYSTEM");}case "USERS"->{online.getItems().clear();String csv=ChatWire.field(p,1);if(!csv.isBlank())online.getItems().addAll(Arrays.asList(csv.split(",")));recipient.getItems().setAll(online.getItems());recipient.getItems().remove(me);onlineCount.setText(online.getItems().size()+" ONLINE");}case "EVENT"->{String type=ChatWire.field(p,1),from=ChatWire.field(p,2),to=ChatWire.field(p,3),body=ChatWire.field(p,4);String label=switch(type){case "PRIVATE"->"Riêng tư · "+from+" → "+to;case "GROUP"->"Nhóm "+to+" · "+from;case "SYSTEM"->"Hệ thống";default->from;};addMessage(label,body,from.equals(me),type);}case "JOINED_GROUP"->addMessage("Hệ thống","Đã tham gia nhóm "+ChatWire.field(p,1),false,"SYSTEM");case "ERROR"->addMessage("Lỗi Server",ChatWire.field(p,1),false,"ERROR");case "PONG"->addMessage("Server","PONG · "+ChatWire.field(p,1),false,"SYSTEM");case "ATTACH_EVENT"->receiveAttachment(ChatWire.field(p,1),ChatWire.field(p,2),ChatWire.field(p,3),ChatWire.field(p,4),ChatWire.field(p,5),ChatWire.field(p,6),ChatWire.field(p,7));default->addMessage("Server",frame,false,"SYSTEM");}});}
    private void addMessage(String who,String body,boolean mine,String kind){VBox bubble=new VBox(5);bubble.setMaxWidth(520);bubble.getStyleClass().add("message-bubble");if(mine)bubble.getStyleClass().add("message-mine");Label sender=new Label(who);sender.setStyle("-fx-font-weight:800;-fx-font-size:11px;");Label text=new Label(body);text.setWrapText(true);Label meta=new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))+"  ·  "+kind);meta.getStyleClass().add("message-meta");bubble.getChildren().addAll(sender,text,meta);HBox row=new HBox(bubble);row.setAlignment(mine?Pos.CENTER_RIGHT:Pos.CENTER_LEFT);messages.getChildren().add(row);messageScroll.layout();messageScroll.setVvalue(1.0);}
    private void joinGroup(){String g=groupName.getText().trim();if(g.isBlank()){alert("Nhập tên nhóm trước.");return;}if(tcp==null||!tcp.isConnected()){alert("Kết nối Server trước.");return;}try{tcp.send(ChatWire.frame("JOIN_GROUP",g));}catch(IOException e){alert(e.getMessage());}}
    private void send(){String body=message.getText().trim();if(body.isBlank())return;if(tcp==null||!tcp.isConnected()){alert("Bạn chưa kết nối Chat Server.");return;}try{switch(mode.getValue()){case "Broadcast · TCP"->tcp.send(ChatWire.frame("BROADCAST",body));case "Private · TCP"->{String to=recipient.getValue();if(to==null)throw new IOException("Hãy chọn người nhận.");tcp.send(ChatWire.frame("PRIVATE",to,body));}case "Group · TCP"->{String group=groupName.getText().trim();if(group.isBlank())throw new IOException("Hãy nhập tên nhóm.");tcp.send(ChatWire.frame("GROUP",group,body));}case "Broadcast · UDP"->udp.sendBroadcast(host.getText().trim(),Integer.parseInt(udpPort.getText().trim()),me,body);case "Multicast · UDP"->udp.sendMulticast(me,body);}message.clear();}catch(Exception ex){alert(ex.getMessage());}}

    private ContextMenu createEmojiPicker() {
        ContextMenu picker = new ContextMenu();
        String[] emojis = {"😀", "😂", "🥰", "😍", "😎", "🤔", "😭", "😴", "👍", "👎", "❤️", "💯", "🔥", "🎉", "🙏", "🙌", "🤝", "✅", "🚀", "👀"};
        for (String value : emojis) {
            MenuItem item = new MenuItem(value);
            item.setOnAction(event -> message.insertText(message.getCaretPosition(), value));
            picker.getItems().add(item);
        }
        return picker;
    }

    private void sendAttachment() {
        if (tcp == null || !tcp.isConnected()) { alert("Kết nối Chat Server trước khi gửi tệp."); return; }
        String selectedMode = mode.getValue();
        String scope;
        String target = "";
        if ("Broadcast · TCP".equals(selectedMode)) scope = "BROADCAST";
        else if ("Private · TCP".equals(selectedMode)) {
            scope = "PRIVATE"; target = recipient.getValue();
            if (target == null || target.isBlank()) { alert("Chọn người nhận trước khi đính kèm."); return; }
        } else if ("Group · TCP".equals(selectedMode)) {
            scope = "GROUP"; target = groupName.getText().trim();
            if (target.isBlank()) { alert("Nhập tên nhóm trước khi đính kèm."); return; }
        } else {
            alert("Tệp/ảnh/audio/video được truyền tin cậy qua TCP. Hãy chọn Broadcast, Private hoặc Group · TCP.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn tệp, ảnh, audio hoặc video để gửi");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tất cả tệp", "*.*"),
                new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"),
                new FileChooser.ExtensionFilter("Audio", "*.mp3", "*.wav", "*.m4a"),
                new FileChooser.ExtensionFilter("Video", "*.mp4", "*.mov", "*.m4v"),
                new FileChooser.ExtensionFilter("Tài liệu", "*.pdf", "*.txt", "*.doc", "*.docx", "*.zip"));
        java.io.File selected = chooser.showOpenDialog(message.getScene().getWindow());
        if (selected == null) return;
        try {
            Path file = selected.toPath();
            long size = Files.size(file);
            if (size < 1 || size > 6L * 1024 * 1024) {
                alert("Kích thước tệp phải từ 1 byte đến 6 MB để truyền qua TCP chat.");
                return;
            }
            String fileName = file.getFileName().toString();
            String extension = fileName.toLowerCase(Locale.ROOT);
            String kind = extension.matches(".*\\.(png|jpg|jpeg|gif|webp|bmp)$") ? "IMAGE"
                    : extension.matches(".*\\.(mp3|wav|m4a|ogg|flac)$") ? "AUDIO"
                    : extension.matches(".*\\.(mp4|mov|m4v|avi|webm)$") ? "VIDEO" : "FILE";
            String mime = Files.probeContentType(file);
            if (mime == null || mime.isBlank()) mime = kind.equals("IMAGE") ? "image/*" : kind.equals("AUDIO") ? "audio/*" : kind.equals("VIDEO") ? "video/*" : "application/octet-stream";
            String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
            tcp.send(ChatWire.frame("ATTACH", scope, target, kind, fileName, mime, encoded));
            setStatus("Đang gửi " + fileName + " qua TCP…");
        } catch (Exception exception) {
            alert("Không gửi được tệp: " + exception.getMessage());
        }
    }

    private void receiveAttachment(String scope, String sender, String target, String kind,
                                   String fileName, String mime, String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            Path folder = Path.of("data", "received-files").toAbsolutePath().normalize();
            Files.createDirectories(folder);
            String safeName = fileName == null ? "attachment.bin" : fileName.replace('\\', '/');
            safeName = safeName.substring(safeName.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
            if (safeName.isBlank() || safeName.equals(".") || safeName.equals("..")) safeName = "attachment.bin";
            Path saved = folder.resolve(System.currentTimeMillis() + "-" + UUID.randomUUID() + "-" + safeName).normalize();
            if (!saved.startsWith(folder)) throw new IOException("Invalid attachment path");
            Files.write(saved, bytes);

            boolean mine = sender.equals(me);
            String label = switch (scope) {
                case "PRIVATE" -> "Riêng tư · " + sender + " → " + target;
                case "GROUP" -> "Nhóm " + target + " · " + sender;
                default -> sender + " · Broadcast";
            };
            VBox bubble = new VBox(7);
            bubble.setMaxWidth(560);
            bubble.getStyleClass().add("message-bubble");
            if (mine) bubble.getStyleClass().add("message-mine");
            Label title = new Label(label); title.setStyle("-fx-font-weight:800;-fx-font-size:11px;");
            String icon = switch (kind) { case "IMAGE" -> "🖼"; case "AUDIO" -> "♫"; case "VIDEO" -> "▶"; default -> "📎"; };
            Label details = new Label(icon + "  " + safeName + "  ·  " + String.format(Locale.ROOT, "%.1f KB", bytes.length / 1024.0));
            details.setStyle("-fx-font-weight:700;");
            bubble.getChildren().addAll(title, details);
            if ("IMAGE".equals(kind)) {
                ImageView preview = new ImageView(new Image(saved.toUri().toString(), 300, 190, true, true));
                preview.setPreserveRatio(true);
                bubble.getChildren().add(preview);
            }
            Button open = new Button(switch (kind) { case "AUDIO" -> "▶ Mở / phát audio"; case "VIDEO" -> "▶ Mở video"; default -> "Mở tệp đã nhận"; });
            open.setOnAction(event -> openReceivedFile(saved));
            Label meta = new Label("Đã lưu · " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + " · " + mime);
            meta.getStyleClass().add("message-meta");
            bubble.getChildren().addAll(open, meta);
            HBox row = new HBox(bubble);
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messages.getChildren().add(row);
            messageScroll.layout(); messageScroll.setVvalue(1.0);
            setStatus("Đã nhận tệp " + safeName);
        } catch (Exception exception) {
            addMessage("Tệp đính kèm lỗi", exception.getMessage(), false, "ERROR");
        }
    }

    private void openReceivedFile(Path file) {
        try {
            if (!Desktop.isDesktopSupported()) throw new IOException("Desktop integration is unavailable");
            Desktop.getDesktop().open(file.toFile());
        } catch (Exception exception) {
            alert("Không mở được tệp: " + exception.getMessage() + "\nTệp đã lưu tại: " + file.toAbsolutePath());
        }
    }

    private void setStatus(String s){Platform.runLater(()->connection.setText(s));}
    private void disconnect(){if(tcp!=null){tcp.close();tcp=null;}if(udp!=null){udp.close();udp=null;}}
    private void alert(String s){Alert a=new Alert(Alert.AlertType.INFORMATION,s,ButtonType.OK);a.setHeaderText("LTM Connect");a.show();}
    @Override public void stop(){disconnect();}
    public static void main(String[] args){launch(args);}
}
