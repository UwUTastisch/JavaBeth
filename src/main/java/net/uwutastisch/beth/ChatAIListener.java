package net.uwutastisch.beth;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class ChatAIListener extends ListenerAdapter {

    private String suffix = "> \n";;

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        Message message = event.getMessage();
        String contentDisplay = message.getContentDisplay();
        long id = event.getJDA().getSelfUser().getIdLong();
        if(event.getAuthor().getIdLong() == id) return;
        if(!(event.getChannel() instanceof TextChannel channel)) return;
        if(!ConfigUtil.isLLMChat(channel)) return;
        if(message.getContentRaw().toLowerCase().startsWith("beth reboot")) {
            channel.sendMessage(ConfigUtil.startMessage).queue();
            return;
        }


        List<Message> messages = channel.getIterableHistory().complete();
        //for (Message message1 : messages) {
        //    System.out.println(message1.getContentDisplay());
        //}
        int size = messages.size();
        int i = 0;
        //for (; i >= 0; i--) {
        //    if(messages.get(i).getContentRaw().equals(ConfigUtil.startMessage)) break;
        //}
        do {
            System.out.println(/*"A" + ConfigUtil.startMessage + "\nB" +*/ messages.get(i).getContentRaw() + " | " + messages.get(i).getTimeCreated());
        } while (!messages.get(i).getContentRaw().equals(ConfigUtil.startMessage) && i++ < size);
        //System.out.println(messages);
        JSONArray context = new JSONArray();
        context.put(new JSONObject().put("role", "system").put("content", ConfigUtil.getSystemPrompt(channel)));
        //new StringBuilder("\"messages\": [").append("{\"role\": \"system\", \"content\": ").append(ConfigUtil.getSystemPrompt(channel)).append("}");
        //System.out.println(i + " messages as Context " + size + " total messages");
        for (int j = i -1; j >= 0; j--) {
            Message m = messages.get(j);
            System.out.println(m);
            long UID = m.getAuthor().getIdLong();
            String c = m.getContentDisplay();
            if(UID == id) {
                context.put(new JSONObject().put("role", "assistant").put("content",c.substring(c.indexOf(suffix) + suffix.length()))); //", {\"role\": \"assistant\", \"content\": \"").append(c.substring(c.indexOf(suffix) + suffix.length())).append("\"}");
            } else {
                System.out.println("UwU -> " + c);
                context.put(new JSONObject().put("role", "user").put("content",c));//context.append(", {\"role\": \"user\", \"content\": \"" + c + "\"}");
            }
        }

        String llmType = ConfigUtil.getLLMType(channel);
        sendMessage(channel, llmType, suffix, context);
        //String contentRaw = message.getContentRaw(); //add unnecessary ids
        //System.out.println(contentDisplay + " vs contentRaw - " + contentRaw);

    }

    private static void sendMessage(TextChannel channel, String llmType, String suffix, JSONArray context) {
        CompletableFuture.runAsync(()-> {
            CompletableFuture<Void> voidCompletableFuture = CompletableFuture.runAsync(() -> {
                //channel.sendMessage("render message").queue()a;
                MessageAction messageAction = channel.sendMessage("<Modell: " + llmType + suffix);

                String s = Main.chatGPT(context);
                System.out.println(s);
                if(s.length() <= 2000) {
                    messageAction.tts(true).append(s).queue();
                } else {
                    for (int i = 0; i < s.length(); i += 2000) {
                        messageAction.tts(true).append(s.substring(i,i+2000)).queue();
                    }
                    messageAction.tts(true).append(s.substring(2000*(s.length()/2000))).queue();
                }
            }).orTimeout(120, TimeUnit.SECONDS).exceptionally(throwable -> {
                System.out.println(throwable + "");
                return null;
            });
            while (!voidCompletableFuture.isDone()) {
                if(!voidCompletableFuture.isCompletedExceptionally() && !voidCompletableFuture.isDone()) {
                    System.out.println("isProcessed");
                    channel.sendTyping().queue();
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    System.out.println("Error: message cant be processed -> " + context);
                    return;
                }
            }
            return;
        });
    }
}