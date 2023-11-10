package net.uwutastisch.beth;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
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
        while (!messages.get(i).getContentRaw().equals(ConfigUtil.startMessage) &&  size > (i+=1)) {
            System.out.println(/*"A" + ConfigUtil.startMessage + "\nB" +*/ messages.get(i).getContentRaw() + " | " + messages.get(i).getTimeCreated());
        }
        //System.out.println(messages);
        JSONArray context = new JSONArray();
        context.put(new JSONObject().put("role", "system").put("content", /*"To tag user: @<userid> "+*/  ConfigUtil.getSystemPrompt(channel)));
        //new StringBuilder("\"messages\": [").append("{\"role\": \"system\", \"content\": ").append(ConfigUtil.getSystemPrompt(channel)).append("}");
        //System.out.println(i + " messages as Context " + size + " total messages");
        for (int j = i -1; j >= 0; j--) {
            Message m = messages.get(j);
            System.out.println(m);
            long UID = m.getAuthor().getIdLong();
            String c = m.getContentRaw();
            if(UID == id) {
                context.put(new JSONObject().put("role", "assistant").put("content",c.substring(c.indexOf(suffix) + suffix.length()))); //", {\"role\": \"assistant\", \"content\": \"").append(c.substring(c.indexOf(suffix) + suffix.length())).append("\"}");
            } else {
                System.out.println("UwU -> " + c);
                User author = m.getAuthor();
                author.getIdLong();
                //Member member = event.getMember();
                //long idLong = m.getIdLong();
                //Member member = ((TextChannel) event.getChannel()).getMembers().stream().filter(mb -> mb.getIdLong() == idLong).findFirst().get();
                //if(member == null){
                //    System.out.println("RIP something went wrong");
                //    return;
                //}
                //String nick = member.getNickname();
                context.put(new JSONObject().put("role", "user").put("content","MessageFormat={user={tagMentionFormat=\"<@" + m.getAuthor().getIdLong() + ">\", name=\"" + /*((nick != null) ? nick :*/ m.getAuthor().getName()/*)*/  + "}\", content=\n" + c +"\n}"));//context.append(", {\"role\": \"user\", \"content\": \"" + c + "\"}");
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

                //String s = Main.chatGPT(context);
                String s;
                int tokens;
                try {
                    JSONObject jsonObject = Main.requestOpenAI(context, "gpt-3.5-turbo");
                    s = Main.getGptMessageContent(jsonObject);
                    tokens = jsonObject.getJSONObject("usage").getInt("total_tokens");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                MessageAction messageAction = channel.sendMessage("<Model= " + llmType +", " + " Tokens=" + tokens + "/"  + 4000 + suffix);

                System.out.println(s);
                if(s.length() <= 2000) {
                    messageAction.tts(true).append(s).queue();
                } else {
                    String substring = s.substring(0, 1800);
                    messageAction.tts(true).append(substring).queue();
                    for (int i = 1800; i < s.length(); i += 1800) {
                        substring = s.substring(i, Math.min(i + 1800, s.length()));
                        System.out.println("length " + substring.length());

                        channel.sendMessage(substring).tts(true).queue();
                        //try {
                        //    TimeUnit.MILLISECONDS.sleep(500);
                        //} catch (InterruptedException e) {
                        //    throw new RuntimeException(e);
                        //}
                    }
                    //messageAction.tts(true).append(s.substring(1800*(s.length()/1800))).queue();
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