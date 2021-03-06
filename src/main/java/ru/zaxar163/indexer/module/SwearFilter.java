package ru.zaxar163.indexer.module;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;

import ru.zaxar163.indexer.Indexer;
import ru.zaxar163.indexer.Utils;

public class SwearFilter {

	private static String normalizeWord(String str) {
		if (str.isEmpty())
			return "";
		final char[] chars = str.toCharArray();
		int len = chars.length;
		int st = 0;
		while (st < len && !Character.isAlphabetic(chars[st]))
			st++;
		while (st < len && !Character.isAlphabetic(chars[len - 1]))
			len--;
		str = st > 0 || len < chars.length ? str.substring(st, len) : str;
		return str.toLowerCase().replace('a', 'а').replace('e', 'е').replace('э', 'е').replace('ё', 'е')
				.replace('y', 'у').replace('p', 'р').replace('x', 'х').replace('o', 'о').replace('c', 'с')
				.replace('s', 'с');
	}

	private final Set<String> badWords;

	private boolean enabled = true;

	private final Set<Long> enabledChannels;

	public SwearFilter(final Indexer indexer) {
		enabledChannels = Collections.newSetFromMap(new ConcurrentHashMap<>());
		badWords = new HashSet<>();
		indexer.client.addMessageCreateListener(event -> {
			if (enabledChannels.contains(Long.valueOf(event.getChannel().getId())))
				checkMessage(event.getMessage());
		});

		indexer.client.addMessageEditListener(event -> {
			if (enabledChannels.contains(Long.valueOf(event.getChannel().getId())) && event.getMessage().isPresent())
				checkMessage(event.getMessage().get());
		});

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream("badwords.txt"), StandardCharsets.UTF_8))) {
			String word;
			while ((word = reader.readLine()) != null) {
				word = SwearFilter.normalizeWord(word.trim());
				if (!word.isEmpty())
					badWords.add(word);
			}
		} catch (final Exception ex) {
			enabled = false;
			System.err.println("SwearFilter disabled. File 'badwords.txt' not found");
			return;
		}

		if (new File("channels.lst").exists())
			try (BufferedReader readerChannels = new BufferedReader(
					new InputStreamReader(new FileInputStream("channels.lst"), StandardCharsets.UTF_8))) {
				String word;
				while ((word = readerChannels.readLine()) != null)
					indexer.client.getChannelById(Long.parseLong(word))
							.ifPresent(t -> t.asTextChannel().ifPresent(this::enableFor));
			} catch (final Exception ex) {
				enabled = false;
				System.err.println("SwearFilter disabled. File 'channels.lst' not found");
				return;
			}
		Utils.filterSrvList(indexer.client, enabledChannels);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try (PrintWriter readerChannels = new PrintWriter(
					new OutputStreamWriter(new FileOutputStream("channels.lst"), StandardCharsets.UTF_8))) {
				enabledChannels.forEach(readerChannels::println);
			} catch (final Exception ex) {
				System.err.println(ex.toString());
			}
		}, "Saving channels thread"));
	}

	private void checkMessage(final Message message) {
		if (message.getAuthor().isYourself())
			return;
		if (hasSwear(message.getContent()))
			message.delete();
	}

	public void disableFor(final TextChannel channel) {
		if (!enabled || !isActive(channel))
			return;
		enabledChannels.remove(channel.getId());
	}

	public void enableFor(final TextChannel channel) {
		if (!enabled || isActive(channel))
			return;
		enabledChannels.add(channel.getId());
	}

	private boolean hasSwear(final String message) {
		for (final String word : message.split(" "))
			if (badWords.contains(SwearFilter.normalizeWord(word)))
				return true;
		return false;
	}

	public boolean isActive(final TextChannel channel) {
		return enabledChannels.contains(channel.getId());
	}

	public boolean isEnabled() {
		return enabled;
	}
}
