package com.wsd.structura.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;

@JavaScript("https://cdn.jsdelivr.net/npm/apexcharts")
public class MainLayout extends AppLayout {

	public MainLayout() {
		setPrimarySection(Section.DRAWER);
		addHeaderContent();
		addDrawerContent();
	}

	private void addHeaderContent() {
		H2 title = new H2("Structura");
		title.addClassNames(LumoUtility.FontSize.LARGE,
				LumoUtility.Margin.NONE,
				LumoUtility.TextColor.PRIMARY_CONTRAST);

		Span tagline = new Span("AI-Powered Structured Products Configurator");
		tagline.addClassNames(LumoUtility.FontSize.SMALL,
				LumoUtility.TextColor.SECONDARY,
				LumoUtility.Margin.Start.MEDIUM);

		HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), title, tagline);
		header.setSpacing(true);
		header.setPadding(true);
		header.setWidthFull();
		header.addClassNames("structura-header");

		addToNavbar(header);
	}

	private void addDrawerContent() {
		SideNav nav = new SideNav();
		nav.addItem(new SideNavItem("Configurator", ClientInputView.class));
		nav.addItem(new SideNavItem("Results", ResultsView.class));
		nav.addItem(new SideNavItem("How AI Helped", HowAIHelpedView.class));
		addToDrawer(nav);
	}
}
