/*-
 * #%L
 * BigDataViewer-Playground
 * %%
 * Copyright (C) 2019 - 2024 Nicolas Chiaruttini, EPFL - Robert Haase, MPI CBG - Christian Tischer, EMBL
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package ch.epfl.biop.registration.scijava.widget;

import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.Priority;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.swing.widget.SwingInputWidget;
import org.scijava.widget.InputWidget;
import org.scijava.widget.WidgetModel;

import javax.swing.*;
import java.util.List;

/**
 * Swing widget for selecting registration pairs in the user interface.
 *
 * @author Nicolas Chiaruttini
 */

@SuppressWarnings("unused")
@Plugin(type = InputWidget.class, priority = Priority.EXTREMELY_HIGH)
public class SwingRegistrationPairWidget extends
	SwingInputWidget<RegistrationPair> implements
	RegistrationPairWidget<JPanel>
{

	@Override
	protected void doRefresh() {}

	@Override
	public boolean supports(final WidgetModel model) {
		return super.supports(model) && model.isType(RegistrationPair.class);
	}

	@Override
	public RegistrationPair getValue() {
		return jList.getSelectedValue();
	}

	JList<RegistrationPair> jList;
	
	@Parameter
	ObjectService objectService;

	List<RegistrationPair> allRegistrations;

	@Override
	public void set(final WidgetModel model) {
		super.set(model);
		allRegistrations = objectService.getObjects(RegistrationPair.class);

		// Convert the ArrayList to an array
		RegistrationPair[] itemArray = new RegistrationPair[allRegistrations.size()];
		allRegistrations.toArray(itemArray);

		jList = new JList<>(itemArray);

		// Set the selection mode to single selection
		jList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		JScrollPane scrollPane = new JScrollPane(jList);

		getComponent().add(scrollPane);
		refreshWidget();
		model.setValue(null);
		jList.addListSelectionListener((e) -> model.setValue(getValue()));

	}

}
