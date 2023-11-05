package org.example;

import net.dv8tion.jda.api.entities.TextChannel;

import java.util.Objects;

public class ConfigUtil {
    public static boolean isLLMChat(TextChannel channel) {
        return channel.canTalk() && Objects.requireNonNull(channel.getTopic()).toLowerCase().startsWith("beth=");
    }

    public static String getLLMType(TextChannel channel) {
        return Objects.requireNonNull(channel.getTopic()).substring(5).split(" ")[0].split("\n")[0];
    }

    public static String getSystemPrompt(TextChannel channel) {
        String s = Objects.requireNonNull(channel.getTopic());
        System.out.println(channel.getTopic());
        int i = s.indexOf("\n");
        if(i <= 0) return "\"\"";
        String substring = s.substring(i);
        if(substring.startsWith("\n")) substring = substring.substring(1);
        return "\" " + substring + "\"";
    }

    public final static String startMessage = "Hewwo, du hast mich neugestartet :D";
}
