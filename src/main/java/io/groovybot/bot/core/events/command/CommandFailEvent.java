package io.groovybot.bot.core.events.command;

import io.groovybot.bot.core.command.Command;
import io.groovybot.bot.core.command.CommandEvent;
import lombok.Getter;

public class CommandFailEvent extends CommandExecutedEvent {

    @Getter
    private final Throwable throwable;

    public CommandFailEvent(CommandEvent event, Command command, Throwable throwable) {
        super(event, command);
        this.throwable = throwable;
    }
}
