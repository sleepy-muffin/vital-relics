package com.example.vitalrelics.common.guide;

import com.example.vitalrelics.common.relics.Translations;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Platform-independent guide layout, rendering, search and input state. */
public final class GuideBookView {
	private static final int MARGIN = 16, LIST_WIDTH = 130, GAP = 8;
	private static final int TITLE_HEIGHT = 11;
	private static final int TAB_HEIGHT = 14, TAB_GAP = 2, TAB_PAD = 7;
	private static final int SEARCH_HEIGHT = 16, SEARCH_GAP = 4, ROW_HEIGHT = 16;
	private static final int LINE_HEIGHT = 10, SCROLL_STEP = 24;
	private static final int SCROLLBAR_WIDTH = 3, MIN_SCROLLBAR_HEIGHT = 12;

	private static final int COLOR_TITLE = 0xFFFFFFFF;
	private static final int COLOR_SUBTITLE = 0xFFAAAAAA;
	private static final int COLOR_TEXT = 0xFFDDDDDD;
	private static final int COLOR_LIST_TEXT = 0xFFCCCCCC;
	private static final int COLOR_ACCENT = 0xFFE8C170;
	private static final int COLOR_TAB_ACTIVE_TEXT = 0xFFFFF4D6;
	private static final int COLOR_TAB_TEXT = 0xFFA8A8A8;

	private final List<Tab> tabs = new ArrayList<>();
	private final List<LinkHit> linkHits = new ArrayList<>();
	private int[] tabLeft = new int[0];
	private int[] tabWidth = new int[0];
	private int width, height, selectedTab;

	public GuideBookView(final GuideBook book, final String locale,
			final Function<String, String> ingredientName,
			final Function<String, String> effectName) {
		if (book == null) throw new IllegalArgumentException("book cannot be null");

		final Tab gettingStarted = new Tab(
				tr("guide.vitalrelics.tab.getting_started", "Getting Started"));
		final Tab relics = new Tab(tr("guide.vitalrelics.tab.relics", "Relics"));
		final Tab passives = new Tab(tr("guide.vitalrelics.tab.passives", "Passives"));
		final Tab spells = new Tab(tr("guide.vitalrelics.tab.spells", "Spells"));

		for (final GuideBook.Entry entry : book.entries())
			relics.pages.add(new Page(GuidePage.from(entry, ingredientName, effectName)));

		for (final GuidePage page : GuidePage.passiveSkills(book))
			passives.pages.add(new Page(page));

		for (final GuidePage page : GuidePage.spells(book))
			spells.pages.add(new Page(page));

		gettingStarted.pages.add(new Page(GuidePage.introduction()));
		for (final String id : List.of("configure-curios", "configure-recipes", "configure-translations")) {
			String markdown = readMarkdown(locale, id);
			if (markdown == null && !"en_us".equals(locale)) markdown = readMarkdown("en_us", id);
			if (markdown != null) gettingStarted.pages.add(new Page(MarkdownPage.parse(id, markdown)));
		}

		tabs.add(gettingStarted);
		tabs.add(relics);
		tabs.add(passives);
		tabs.add(spells);

		for (final Tab tab : tabs)
			tab.visible.addAll(tab.pages);

		tabLeft = new int[tabs.size()];
		tabWidth = new int[tabs.size()];
	}

	private static String readMarkdown(final String locale, final String id) {
		final String path = "/vitalrelics/guide/" + locale + "/" + id + ".md";
		try (InputStream stream = GuideBookView.class.getResourceAsStream(path)) {
			return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new RuntimeException("Failed to load guide page " + path, exception);
		}
	}

	public void resize(final int width, final int height) {
		this.width = width;
		this.height = height;
		for (final Tab tab : tabs) {
			clampListScroll(tab);
			clampContentScroll(tab);
		}
	}

	public int searchX() { return MARGIN; }
	public int searchY() { return tabBottom() + 4; }
	public int searchWidth() { return LIST_WIDTH; }
	public int searchHeight() { return SEARCH_HEIGHT; }

	public static String translate(final String key, final String fallback) {
		return Translations.get().translate(key, fallback);
	}

	private int tabTop() {
		return MARGIN + TITLE_HEIGHT;
	}

	private int tabBottom() {
		return tabTop() + TAB_HEIGHT;
	}

	private int listLeft() {
		return MARGIN;
	}

	private int listRight() {
		return MARGIN + LIST_WIDTH;
	}

	private int contentLeft() {
		return listRight() + GAP;
	}

	private int listTop() {
		return searchY() + SEARCH_HEIGHT + SEARCH_GAP;
	}

	private int contentTop() {
		return tabBottom() + 4;
	}

	private int paneBottom() {
		return height - MARGIN;
	}

	private int listHeight() {
		return Math.max(0, paneBottom() - listTop());
	}

	private int contentHeight() {
		return Math.max(0, paneBottom() - contentTop());
	}

	private int listTextRight() {
		return listRight() - SCROLLBAR_WIDTH - 3;
	}

	private int contentTextRight() {
		return width - MARGIN - SCROLLBAR_WIDTH - 3;
	}

	private Tab currentTab() {
		selectedTab = Math.max(0, Math.min(selectedTab, tabs.size() - 1));
		return tabs.get(selectedTab);
	}

	private Page selectedPage(final Tab tab) {
		if (tab.visible.isEmpty())
			return null;

		tab.selectedIndex = Math.max(
				0,
				Math.min(tab.selectedIndex, tab.visible.size() - 1)
		);

		return tab.visible.get(tab.selectedIndex);
	}

	public void updateSearch(final String query) {
		final String normalized = query == null
				? ""
				: query.strip().toLowerCase(Locale.ROOT);

		for (final Tab tab : tabs) {
			final Page previous = tab.visible.isEmpty()
					? null
					: tab.visible.get(Math.max(0, Math.min(tab.selectedIndex, tab.visible.size() - 1)));

			tab.visible.clear();

			for (final Page page : tab.pages) {
				if (matchesSearch(page, normalized))
					tab.visible.add(page);
			}

			tab.listScroll = 0;
			tab.contentScroll = 0;

			if (tab.visible.isEmpty()) {
				tab.selectedIndex = 0;
				continue;
			}

			final int previousIndex = tab.visible.indexOf(previous);
			tab.selectedIndex = previousIndex >= 0 ? previousIndex : 0;
		}
	}

	private static boolean matchesSearch(final Page page, final String query) {
		if (query.isEmpty())
			return true;

		if (contains(page.title, query) || contains(page.id, query))
			return true;

		if (page.guide != null) {
			if (contains(page.guide.description, query))
				return true;

			for (final GuidePage.Section section : page.guide.sections) {
				if (contains(section.title, query))
					return true;

				for (final String line : section.lines) {
					if (contains(line, query))
						return true;
				}
			}
		}

		if (page.markdown != null) {
			for (final MarkdownPage.Block block : page.markdown.blocks) {
				if (block instanceof MarkdownPage.Heading heading && contains(heading.text(), query))
					return true;
				if (block instanceof MarkdownPage.Paragraph paragraph && contains(paragraph.text(), query))
					return true;
				if (block instanceof MarkdownPage.ListItem item && contains(item.text(), query))
					return true;
			}
		}

		return false;
	}

	private static boolean contains(final String value, final String query) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(query);
	}

	public void render(final GuideCanvas graphics) {
		final Tab tab = currentTab();
		linkHits.clear();

		graphics.text(tr("guide.vitalrelics.title", "Vital Relics"), MARGIN, MARGIN, COLOR_TITLE);
		renderTabs(graphics);

		renderPanel(graphics, listLeft(), listTop(), listRight(), paneBottom());
		renderPanel(graphics, contentLeft(), contentTop(), width - MARGIN, paneBottom());

		renderPageList(graphics, tab);

		final Page page = selectedPage(tab);

		if (page == null) {
			graphics.text(tr("guide.vitalrelics.empty", "Nothing to show here."),
					contentLeft() + 4, contentTop() + 4, 0xFFAAAAAA);
		} else if (page.markdown != null) {
			renderMarkdownPage(graphics, tab, page.markdown);
		} else {
			renderGuidePage(graphics, tab, page.guide);
		}

		renderScrollbars(graphics, tab);
	}

	private void renderTabs(final GuideCanvas graphics) {
		final int top = tabTop();
		final int count = tabs.size();
		final int available = Math.max(0,
				(width - MARGIN * 2) - TAB_GAP * Math.max(0, count - 1));
		final int maxWidth = count == 0 ? 0 : available / count;

		int x = MARGIN;

		for (int i = 0; i < count; ++i) {
			final Tab tab = tabs.get(i);
			final int desired = graphics.textWidth(tab.title) + TAB_PAD * 2;
			int cellWidth = Math.max(0, Math.min(desired, maxWidth));

			if (cellWidth <= 0) {
				tabLeft[i] = x;
				tabWidth[i] = 0;
				continue;
			}

			final boolean active = i == selectedTab;

			graphics.fill(x, top, x + cellWidth, top + TAB_HEIGHT,
					active ? 0x55E8C170 : 0x22FFFFFF);

			if (active) {
				graphics.fill(x, top + TAB_HEIGHT - 2, x + cellWidth, top + TAB_HEIGHT, COLOR_ACCENT);
			}

			graphics.text(
					fitText(graphics, tab.title, Math.max(0, cellWidth - TAB_PAD * 2)),
					x + TAB_PAD,
					top + 3,
					active ? COLOR_TAB_ACTIVE_TEXT : COLOR_TAB_TEXT
			);

			tabLeft[i] = x;
			tabWidth[i] = cellWidth;
			x += cellWidth + TAB_GAP;
		}

		graphics.fill(MARGIN, tabBottom(), width - MARGIN, tabBottom() + 1, 0x30FFFFFF);
	}

	private static String fitText(final GuideCanvas graphics, final String text, final int maxWidth) {
		if (maxWidth <= 0)
			return "";
		if (graphics.textWidth(text) <= maxWidth)
			return text;

		final String ellipsis = "...";
		int end = text.length();

		while (end > 0 && graphics.textWidth(text.substring(0, end)) + graphics.textWidth(ellipsis) > maxWidth)
			--end;

		return text.substring(0, end) + ellipsis;
	}

	private static void renderPanel(
			final GuideCanvas graphics,
			final int left, final int top,
			final int right, final int bottom) {

		if (right <= left || bottom <= top)
			return;

		graphics.fill(left, top, right, bottom, 0x24101010);
		graphics.fill(left, top, right, top + 1, 0x30FFFFFF);
		graphics.fill(left, top, left + 1, bottom, 0x30FFFFFF);
		graphics.fill(left, bottom - 1, right, bottom, 0x40000000);
		graphics.fill(right - 1, top, right, bottom, 0x40000000);
	}

	private void renderPageList(final GuideCanvas graphics, final Tab tab) {
		final int left = listLeft();
		final int right = listTextRight();
		final int top = listTop();
		final int bottom = paneBottom();

		tab.listContentHeight = tab.visible.size() * ROW_HEIGHT;
		clampListScroll(tab);

		graphics.pushClip(left, top, right, bottom);

		int y = top - tab.listScroll;

		for (int i = 0; i < tab.visible.size(); ++i) {
			final Page page = tab.visible.get(i);
			final boolean selected = i == tab.selectedIndex;

			if (selected) {
				graphics.fill(left, y, right, y + ROW_HEIGHT - 1, 0x40FFFFFF);
				graphics.fill(left, y, left + 2, y + ROW_HEIGHT - 1, COLOR_ACCENT);
			}

			if (y + ROW_HEIGHT >= top && y <= bottom) {
				graphics.text(
						page.title,
						left + 5, y + 4,
						selected ? 0xFFFFFFFF : COLOR_LIST_TEXT
				);
			}

			y += ROW_HEIGHT;
		}

		graphics.popClip();
	}

	private void renderGuidePage(
			final GuideCanvas graphics,
			final Tab tab,
			final GuidePage page) {

		final int left = contentLeft() + 4;
		final int right = contentTextRight();
		final int top = contentTop();
		final int bottom = paneBottom();
		final int textWidth = Math.max(80, right - left);

		graphics.pushClip(left, top, right, bottom);

		int y = top - tab.contentScroll + 4;

		y = drawLine(graphics, page.title, left, y, COLOR_TITLE);

		final List<String> subtitle = new ArrayList<>();

		if (!page.rarity.isBlank()) {
			subtitle.add(tr(
					"guide.vitalrelics.rarity." + page.rarity,
					humanize(page.rarity)
			));
		}

		if (!page.slot.isBlank()) {
			subtitle.add(tr(
					"guide.vitalrelics.slot." + page.slot,
					humanize(page.slot)
			));
		}

		if (!subtitle.isEmpty())
			y = drawLine(graphics, String.join(" · ", subtitle), left, y, COLOR_SUBTITLE);

		y += 3;

		y = drawWrapped(
				graphics,
				page.description,
				left, y, textWidth,
				COLOR_TEXT
		);

		y += 5;

		for (final GuidePage.Section section : page.sections) {
			y = drawLine(graphics, section.title, left, y, COLOR_TITLE);

			for (final String line : section.lines) {
				y = drawWrapped(
						graphics,
						line,
						left + 5, y,
						Math.max(40, textWidth - 5),
						COLOR_LIST_TEXT
				);
			}

			y += 4;
		}

		if (!page.links.isEmpty()) {
			y = drawLine(
					graphics,
					tr("guide.vitalrelics.section.see_also", "See Also"),
					left, y, COLOR_TITLE
			);

			for (final GuidePage.Link link : page.links) {
				final String label = "▸ " + link.text;
				final int linkLeft = left + 5;
				final int linkWidth = graphics.textWidth(label);

				if (y + LINE_HEIGHT >= top && y + LINE_HEIGHT <= bottom) {
					graphics.text(label, linkLeft, y, COLOR_ACCENT);
					graphics.fill(
							linkLeft, y + LINE_HEIGHT - 1,
							linkLeft + linkWidth, y + LINE_HEIGHT,
							0x80E8C170
					);
					linkHits.add(new LinkHit(
							linkLeft, y,
							linkLeft + linkWidth, y + LINE_HEIGHT,
							link.targetId
					));
				}

				y += LINE_HEIGHT;
			}

			y += 4;
		}

		graphics.popClip();

		tab.pageContentHeight = y - (top - tab.contentScroll) + 4;
		clampContentScroll(tab);
	}

	private void renderMarkdownPage(
			final GuideCanvas graphics,
			final Tab tab,
			final MarkdownPage page) {

		final int left = contentLeft() + 4;
		final int right = contentTextRight();
		final int top = contentTop();
		final int bottom = paneBottom();
		final int textWidth = Math.max(80, right - left);

		graphics.pushClip(left, top, right, bottom);

		int y = top - tab.contentScroll + 4;

		y = drawLine(graphics, page.title, left, y, COLOR_TITLE);
		y += 4;

		for (final MarkdownPage.Block block : page.blocks) {
			if (block instanceof MarkdownPage.Heading heading) {
				y += heading.level() <= 2 ? 5 : 2;
				y = drawLine(
						graphics,
						heading.text(),
						left + Math.min(8, Math.max(0, heading.level() - 2) * 3),
						y,
						heading.level() <= 2 ? COLOR_TITLE : 0xFFE0E0E0
				);
				y += 2;
				continue;
			}

			if (block instanceof MarkdownPage.Paragraph paragraph) {
				y = drawWrapped(
						graphics,
						paragraph.text(),
						left, y,
						textWidth,
						COLOR_TEXT
				);
				y += 4;
				continue;
			}

			if (block instanceof MarkdownPage.ListItem item) {
				final int indent = Math.min(24, item.indent() * 2);
				final String marker = item.ordered()
						? item.marker() + ". "
						: "• ";

				y = drawWrapped(
						graphics,
						marker + item.text(),
						left + 5 + indent, y,
						Math.max(40, textWidth - 5 - indent),
						COLOR_LIST_TEXT
				);
				continue;
			}

			if (block instanceof MarkdownPage.CodeBlock code) {
				if (!code.language().isBlank()) {
					y = drawLine(
							graphics,
							code.language(),
							left + 4, y,
							0xFF999999
					);
				}

				for (final String line : code.lines()) {
					final int lineHeight = Math.max(
							LINE_HEIGHT,
							graphics.wrap(
									line.isEmpty() ? " " : line,
									Math.max(40, textWidth - 10)
							).size() * LINE_HEIGHT
					);

					graphics.fill(
							left,
							y - 1,
							right,
							y + lineHeight,
							0x50202020
					);

					y = drawWrapped(
							graphics,
							line.isEmpty() ? " " : line,
							left + 5, y,
							Math.max(40, textWidth - 10),
							0xFFD0D0D0
					);
				}

				y += 5;
				continue;
			}

			if (block instanceof MarkdownPage.Table table) {
				if (MarkdownPage.isTableSeparator(table))
					continue;

				y = drawWrapped(
						graphics,
						String.join("  |  ", table.cells()),
						left + 4, y,
						Math.max(40, textWidth - 8),
						COLOR_LIST_TEXT
				);
			}
		}

		graphics.popClip();

		tab.pageContentHeight = y - (top - tab.contentScroll) + 4;
		clampContentScroll(tab);
	}

	private int drawLine(
			final GuideCanvas graphics, final String text,
			final int x, final int y, final int color) {

		if (text == null || text.isBlank())
			return y;

		graphics.text(text, x, y, color);
		return y + LINE_HEIGHT;
	}

	private int drawWrapped(
			final GuideCanvas graphics, final String text,
			final int x, final int y,
			final int width, final int color) {

		if (text == null || text.isBlank())
			return y;

		int currentY = y;

		for (final var line : graphics.wrap(text, width)) {
			graphics.text(line, x, currentY, color);
			currentY += LINE_HEIGHT;
		}

		return currentY;
	}

	private void renderScrollbars(final GuideCanvas graphics, final Tab tab) {
		renderScrollbar(
				graphics,
				listRight() - SCROLLBAR_WIDTH,
				listTop(), listHeight(),
				tab.listScroll, tab.listContentHeight
		);

		renderScrollbar(
				graphics,
				width - MARGIN - SCROLLBAR_WIDTH,
				contentTop(), contentHeight(),
				tab.contentScroll, tab.pageContentHeight
		);
	}

	private void renderScrollbar(
			final GuideCanvas graphics, final int x,
			final int top, final int viewportHeight,
			final int scroll, final int contentHeight) {

		if (contentHeight <= viewportHeight || viewportHeight <= 0)
			return;

		final int maxScroll = contentHeight - viewportHeight;

		final int thumbHeight = Math.max(
				MIN_SCROLLBAR_HEIGHT,
				(int) ((double) viewportHeight * viewportHeight / contentHeight)
		);

		final int travel = viewportHeight - thumbHeight;
		final int thumbOffset = (int) ((double) scroll / maxScroll * travel);

		graphics.fill(
				x, top,
				x + SCROLLBAR_WIDTH,
				top + viewportHeight,
				0x40202020
		);

		graphics.fill(
				x, top + thumbOffset,
				x + SCROLLBAR_WIDTH,
				top + thumbOffset + thumbHeight,
				0xFFAAAAAA
		);
	}

	public boolean mouseClicked(
			final double mouseX, final double mouseY, final int button) {

		if (button != 0)
			return false;

		for (int i = 0; i < tabs.size(); ++i) {
			if (inside(
					mouseX, mouseY,
					tabLeft[i], tabTop(),
					tabLeft[i] + tabWidth[i], tabBottom()
			)) {
				selectedTab = i;
				return true;
			}
		}

		for (final LinkHit hit : linkHits) {
			if (inside(mouseX, mouseY, hit.left, hit.top, hit.right, hit.bottom)
					&& openPage(hit.targetId)) {
				return true;
			}
		}

		final Tab tab = currentTab();

		if (inside(
				mouseX, mouseY,
				listLeft(), listTop(),
				listTextRight(), paneBottom()
		)) {
			final int index =
					((int) mouseY - listTop() + tab.listScroll) / ROW_HEIGHT;

			if (index >= 0 && index < tab.visible.size()) {
				tab.selectedIndex = index;
				tab.contentScroll = 0;
				return true;
			}
		}

		return false;
	}

	public boolean mouseScrolled(
			final double mouseX, final double mouseY, final double scrollY) {

		final Tab tab = currentTab();

		if (inside(
				mouseX, mouseY,
				listLeft(), listTop(),
				listRight(), paneBottom()
		)) {
			tab.listScroll -= (int) Math.round(scrollY * SCROLL_STEP);
			clampListScroll(tab);
			return true;
		}

		if (inside(
				mouseX, mouseY,
				contentLeft(), contentTop(),
				width - MARGIN, paneBottom()
		)) {
			tab.contentScroll -= (int) Math.round(scrollY * SCROLL_STEP);
			clampContentScroll(tab);
			return true;
		}

		return false;
	}

	private boolean openPage(final String id) {
		for (int index = 0; index < tabs.size(); ++index) {
			final Tab tab = tabs.get(index);

			for (int page = 0; page < tab.visible.size(); ++page) {
				if (!tab.visible.get(page).id.equals(id))
					continue;

				selectedTab = index;
				tab.selectedIndex = page;
				tab.listScroll = 0;
				tab.contentScroll = 0;
				return true;
			}
		}

		return false;
	}

	private void clampListScroll(final Tab tab) {
		tab.listScroll = clampScroll(tab.listScroll, tab.listContentHeight, listHeight());
	}

	private void clampContentScroll(final Tab tab) {
		tab.contentScroll = clampScroll(
				tab.contentScroll,
				tab.pageContentHeight,
				contentHeight()
		);
	}

	private static int clampScroll(
			final int scroll, final int contentHeight,
			final int viewportHeight) {

		final int max = Math.max(0, contentHeight - viewportHeight);
		return Math.max(0, Math.min(scroll, max));
	}

	private static boolean inside(
			final double x, final double y,
			final int left, final int top,
			final int right, final int bottom) {

		return x >= left && x < right && y >= top && y < bottom;
	}

	private static String tr(final String key, final String fallback) {
		return Translations.get().translate(key, fallback);
	}

	private static String humanize(final String value) {
		if (value == null || value.isBlank())
			return "";

		final String normalized = value.replace('_', ' ').replace('-', ' ');
		final String[] words = normalized.split("\\s+");
		final StringBuilder result = new StringBuilder();

		for (final String word : words) {
			if (word.isEmpty())
				continue;

			if (!result.isEmpty())
				result.append(' ');

			result.append(Character.toUpperCase(word.charAt(0)));

			if (word.length() > 1)
				result.append(word.substring(1));
		}

		return result.toString();
	}

	private static final class LinkHit {
		final int left, top, right, bottom;
		final String targetId;

		LinkHit(
				final int left, final int top,
				final int right, final int bottom,
				final String targetId) {

			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
			this.targetId = targetId;
		}
	}

	private static final class Tab {
		final String title;
		final List<Page> pages = new ArrayList<>();
		final List<Page> visible = new ArrayList<>();
		int selectedIndex, listScroll, contentScroll;
		int listContentHeight, pageContentHeight;

		Tab(final String title) {
			this.title = title;
		}
	}

	private static final class Page {
		final String id, title;
		final GuidePage guide;
		final MarkdownPage markdown;

		Page(final GuidePage guide) {
			this.id = guide.id;
			this.title = guide.title;
			this.guide = guide;
			this.markdown = null;
		}

		Page(final MarkdownPage markdown) {
			this.id = markdown.id;
			this.title = markdown.title;
			this.guide = null;
			this.markdown = markdown;
		}
	}
}
