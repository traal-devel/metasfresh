/**
 *
 */
package de.metas.picking.terminal.form.swing;

/*
 * #%L
 * de.metas.swat.base
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import java.awt.Cursor;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.adempiere.ad.service.IDeveloperModeBL;
import org.compiere.apps.AEnv;
import org.compiere.apps.Waiting;
import org.compiere.apps.form.FormFrame;
import org.compiere.minigrid.IDColumn;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.I_C_BPartner;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;

import de.metas.adempiere.form.terminal.IComponent;
import de.metas.adempiere.form.terminal.IConfirmPanel;
import de.metas.adempiere.form.terminal.ITerminalBasePanel;
import de.metas.adempiere.form.terminal.ITerminalFactory;
import de.metas.adempiere.form.terminal.ITerminalScrollPane;
import de.metas.adempiere.form.terminal.ITerminalScrollPane.ScrollPolicy;
import de.metas.adempiere.form.terminal.ITerminalTable;
import de.metas.adempiere.form.terminal.ITerminalTextPane;
import de.metas.adempiere.form.terminal.TerminalException;
import de.metas.adempiere.form.terminal.context.ITerminalContext;
import de.metas.adempiere.form.terminal.context.ITerminalContextReferences;
import de.metas.adempiere.form.terminal.swing.TerminalSubPanel;
import de.metas.adempiere.form.terminal.swing.TerminalTable;
import de.metas.i18n.IMsgBL;
import de.metas.inoutcandidate.model.I_M_ShipmentSchedule;
import de.metas.picking.legacy.form.IPackingDetailsModel;
import de.metas.picking.legacy.form.ITableRowSearchSelectionMatcher;
import de.metas.picking.legacy.form.MvcMdGenForm;
import de.metas.picking.legacy.form.PackingPanel;
import de.metas.picking.legacy.form.PackingDetailsMd;
import de.metas.picking.legacy.form.PackingMd;
import de.metas.picking.legacy.form.TableRow;
import de.metas.picking.legacy.form.TableRowKey;
import de.metas.picking.terminal.IPackingStateProvider;
import de.metas.picking.terminal.PickingOKPanel;
import de.metas.picking.terminal.Utils;
import de.metas.picking.terminal.Utils.PackingStates;
import de.metas.picking.terminal.form.swing.SwingPickingTerminalPanel.ResetFilters;
import de.metas.process.ProcessExecutionResult;
import de.metas.process.ProcessInfo;
import de.metas.util.Check;
import de.metas.util.Services;
import net.miginfocom.swing.MigLayout;

/**
 * Picking First Window Panel, which is embedded in {@link SwingPickingTerminalPanel}.
 *
 * @author cg
 *
 */
public class SwingPickingOKPanel extends PackingPanel implements PickingOKPanel
{
	protected final String ACTION_Switch = "Switch";
	protected final String ACTION_Today = "Today";

	public static final String COLUMNNAME_ROWID = "ROWID";
	public static final String COLUMNNAME_M_Product_ID = I_M_ShipmentSchedule.COLUMNNAME_M_Product_ID;
	public static final String COLUMNNAME_Qty = "Qty";
	public static final String COLUMNNAME_DeliveryDate = I_M_ShipmentSchedule.COLUMNNAME_DeliveryDate;
	public static final String COLUMNNAME_PreparationDate = de.metas.tourplanning.model.I_M_ShipmentSchedule.COLUMNNAME_PreparationDate;
	public static final String COLUMNNAME_BPValue = I_C_BPartner.COLUMNNAME_Value;
	public static final String COLUMNNAME_C_BPartner_Location_ID = I_M_ShipmentSchedule.COLUMNNAME_C_BPartner_Location_ID;
	public static final String COLUMNNAME_M_Warehouse_Dest_ID = I_M_ShipmentSchedule.COLUMNNAME_M_Warehouse_Dest_ID;
	/**
	 * Barcode matching type column
	 */
	public static final String COLUMNNAME_MatchingType = "B";

	private class PickingSubPanel extends TerminalSubPanel
	{
		private class ConfirmPanelListener implements PropertyChangeListener
		{
			@Override
			public void propertyChange(PropertyChangeEvent evt)
			{
				if (!IConfirmPanel.PROP_Action.equals(evt.getPropertyName()))
				{
					return;
				}
				final String action = String.valueOf(evt.getNewValue());
				if (IConfirmPanel.ACTION_OK.equals(action))
				{
					final ITerminalBasePanel terminalBasePanel = getTerminalBasePanel();
					if (terminalBasePanel != null)
					{
						terminalBasePanel.updateInfo();
					}
				}
				else if (IConfirmPanel.ACTION_Cancel.equals(action))
				{
					final FormFrame framePicking = getPickingFrame();
					framePicking.dispose();
				}
				else if (ACTION_Switch.equals(action))
				{
					getModel().setGroupByWarehouseDest(!getModel().isGroupByWarehouseDest());
					lines = createTerminalTable();
					getModel().setMiniTable(lines);
					configureMiniTable(getModel().getMiniTable());
					refresh();
					//
					scroll.setViewport(lines);
				}
				else if (ACTION_Today.equals(action))
				{
					final boolean displayTodayEntriesOnly = getModel().isDisplayTodayEntriesOnly();
					getModel().setDisplayTodayEntriesOnly(!displayTodayEntriesOnly);
					refresh();
				}
			}
		}

		public ITerminalTable lines;
		private ITerminalScrollPane scroll;

		public PickingSubPanel(ITerminalBasePanel basePanel)
		{
			super(basePanel);
		}

		private ITerminalTable createTerminalTable()
		{
			final SpecialTerminalTable table = new SpecialTerminalTable(getTerminalContext());
			table.setAutoResize(true);
			table.setPackingStateProvider(new PackingStateProvider(table));
			return table;
		}

		@Override
		protected void init()
		{
			lines = createTerminalTable();
			final PackingMd model = createPackingModel(lines);
			setModel(model);

			initComponents();
			initLayout();
		}

		private void initLayout()
		{
			scroll = getTerminalFactory().createScrollPane(lines);
			scroll.setHorizontalScrollBarPolicy(ScrollPolicy.WHEN_NEEDED);
			add(scroll, "dock north, spanx, growy, pushy, h 70%::");

			getUI().setLayout(new MigLayout("ins 0 0", "[fill][grow]", "[nogrid]unrel[||]"));
			//
			add(confirmPanel, "dock south");
		}

		private void initComponents()
		{
			confirmPanel = getTerminalFactory().createConfirmPanel(true, Utils.getButtonSize());
			confirmPanel.addListener(new ConfirmPanelListener());
		}
	}

	class PackingStateProvider implements IPackingStateProvider
	{
		private final TerminalTable table;

		public PackingStateProvider(final TerminalTable table)
		{
			super();
			this.table = table;
		}

		@Override
		public PackingStates getPackingState(int row)
		{
			return null;
		}
	};

	/**
	 * parent panel
	 */
	private final SwingPickingTerminalPanel pickingTerminalPanel;
	private final PickingSubPanel pickingPanel;
	private IConfirmPanel confirmPanel;
	private AbstractPackageTerminal packageTerminal = null; // instantiated by validateSuggestion()
	private final WindowListener packageTerminalWindowListener = new WindowAdapter()
	{
		@Override
		public void windowClosed(WindowEvent e)
		{
			// NOTE: in some environments we had complains that the lines are not refreshed.
			// Even though i could not reproduce in my environment,
			// i've decided to postone the refreshing right after other evens are processed.
			SwingUtilities.invokeLater(() -> onPackageTerminalClosed());
		}
	};

	public SwingPickingOKPanel(final SwingPickingTerminalPanel basePanel)
	{
		super();

		this.pickingTerminalPanel = basePanel;
		this.pickingPanel = new PickingSubPanel(basePanel);

		basePanel.getTerminalContext().addToDisposableComponents(this);
	}

	@Override
	public PackingMd getModel()
	{
		final PackingMd model = super.getModel();
		return model;
	}

	protected IConfirmPanel getConfirmPanel()
	{
		return confirmPanel;
	}

	protected void setEnabled(final boolean enabled)
	{
		if (isDisposed())
		{
			return;
		}

		final FormFrame pickingFrame = getPickingFrame();
		if (pickingFrame == null)
		{
			return;
		}

		pickingFrame.setEnabled(enabled);
		if (enabled)
		{
			pickingFrame.toFront();

			// Ask parent to give focus to right editing component
			final SwingPickingTerminalPanel parent = getPickingTerminalPanel();
			parent.requestFocus();
		}
		else
		{
			pickingFrame.toBack();
		}
	}

	/**
	 *
	 * @return i.e. parent panel
	 */
	public SwingPickingTerminalPanel getPickingTerminalPanel()
	{
		return pickingTerminalPanel;
	}

	@Override
	public FormFrame getPackageFrame()
	{
		if (packageTerminal != null)
		{
			return packageTerminal.getFrame();
		}
		return null;
	}

	@Override
	public FormFrame getPickingFrame()
	{
		if (pickingTerminalPanel != null)
		{
			return pickingTerminalPanel.getComponent();
		}
		return null;
	}

	public final ITerminalContext getTerminalContext()
	{
		return getPickingTerminalPanel().getTerminalContext();
	}

	protected PackingMd createPackingModel(final ITerminalTable lines)
	{
		final ITerminalContext terminalContext = getTerminalContext();
		final PackingMd model = new PackingMd(
				terminalContext.getWindowNo(),
				terminalContext.getAD_User_ID());
		model.setMiniTable(lines);
		return model;
	}

	/**
	 * Used to be able to dispose components that are created while the "packing" terminal window is open.
	 *
	 * @task https://github.com/metasfresh/metasfresh/issues/1911
	 */
	private ITerminalContextReferences packageTerminalRefs;

	@Override
	protected final void executePacking(final IPackingDetailsModel detailsModel)
	{
		//
		// Cleanup old Package Terminal (if any)
		final AbstractPackageTerminal packageTerminalOld = this.packageTerminal;
		if (packageTerminalOld != null && packageTerminalOld.getFrame() != null)
		{
			packageTerminalOld.getFrame().removeWindowListener(packageTerminalWindowListener);
			getTerminalContext().deleteReferences(packageTerminalRefs); // gh #1911
		}
		packageTerminal = null;

		packageTerminalRefs = getTerminalContext().newReferences(); // gh #1911

		//
		// Create and setup new Package Terminal
		final AbstractPackageTerminal packageTerminalNew = createPackingTerminal(detailsModel);
		final ITerminalContext terminalContext = getTerminalContext();
		final FormFrame packageTerminalNewFrame = new FormFrame();
		packageTerminalNew.init(terminalContext.getWindowNo(), packageTerminalNewFrame);
		packageTerminalNewFrame.addWindowListener(packageTerminalWindowListener);
		packageTerminal = packageTerminalNew;

		AEnv.showMaximized(packageTerminalNewFrame);

		//
		// Disable this window
		// NOTE: it will be enabled again onPackageTerminalClosed()
		setEnabled(false);
	}

	protected AbstractPackageTerminal createPackingTerminal(final IPackingDetailsModel detailsModel)
	{
		Check.assumeInstanceOf(detailsModel, PackingDetailsMd.class, "detailsModel");
		final PackingDetailsMd packingDetailsModel = (PackingDetailsMd)detailsModel;

		final SwingPackageTerminal packageTerminal = new SwingPackageTerminal(this, packingDetailsModel);
		return packageTerminal;
	}

	/**
	 * Called when Package Terminal was closed
	 */
	private final void onPackageTerminalClosed()
	{
		//
		// Cleanup current package terminal
		if (packageTerminal != null)
		{
			packageTerminal = null;
		}

		unlockShipmentSchedules(); // task 08153: make sure we unlock *and* update the scheds we did picking on

		getTerminalContext().deleteReferences(packageTerminalRefs); // gh #1911

		//
		// If this window was already disposed (i.e. user closed all windows all together, e.g. on logout) then do nothing
		if (isDisposed())
		{
			return;
		}

		//
		// Put Picking Window (first window) on front and refresh the current lines

		//
		// Ask parent to refresh lines
		{
			final PackingMd model = getModel();
			model.setRequeryNeeded();

			final SwingPickingTerminalPanel parent = getPickingTerminalPanel();
			parent.refreshLines(ResetFilters.IfNoResult);
		}

		setEnabled(true);
	}

	@Override
	public IComponent getComponent()
	{
		return pickingPanel;
	}

	@Override
	public void refresh()
	{
		executeQuery();
	}

	@Override
	public void unlockUI(final ProcessInfo pi)
	{
		getModel().uiLocked = false;
		//
		if (waitIndicator != null)
		{
			waitIndicator.disposeInEDT();
			waitIndicator = null;
		}

		pickingPanel.getComponent().setCursor(Cursor.getDefaultCursor());

		final FormFrame framePicking = getPickingFrame();
		framePicking.setEnabled(true);
		//
		final ProcessExecutionResult result = pi.getResult();
		final StringBuilder iText = new StringBuilder();
		iText.append("<b>") //
				.append(result.getSummary()) //
				.append("</b><br>(") //
				.append(Services.get(IMsgBL.class).getMsg(Env.getCtx(), "Belegerstellung")) //
				.append(")<br>") //
				.append(result.getLogInfo(true));

		final SwingPickingTerminalPanel pickPanel = ((SwingPickingTerminalPanel)pickingPanel.getTerminalBasePanel());
		final ITerminalTextPane text = pickPanel.resultTextPane;
		text.setText(iText.toString());
		pickPanel.next();
		pickPanel.getComponent().toFront();
	}

	private Waiting waitIndicator;

	@Override
	public void lockUI(ProcessInfo pi)
	{
		if (waitIndicator != null)
		{
			waitIndicator.disposeInEDT();
			waitIndicator = null;
		}

		final FormFrame framePicking = getPickingFrame();

		waitIndicator = new Waiting(framePicking, Services.get(IMsgBL.class).getMsg(Env.getCtx(), "Processing"), false, 0);
		waitIndicator.toFront();
		waitIndicator.setVisible(true);

		framePicking.setEnabled(false);

		pickingPanel.getComponent().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		getModel().uiLocked = true;
	}

	@Override
	protected void displayError(Throwable e)
	{
		final ITerminalFactory factory = pickingPanel.getTerminalFactory();
		factory.showWarning(pickingPanel, "Error", new TerminalException(e));
	}

	private void setValueAt(final IMiniTable miniTable, final String columnName, final int rowIndex, final Object value)
	{
		final Integer columnIndex = columnName2index.get(columnName);
		if (columnIndex == null)
		{
			// If column is missing, then we just skip setting the value
			return;
		}

		miniTable.setValueAt(value, rowIndex, columnIndex);
	}

	@Override
	protected void updateMiniTable()
	{
		final IMiniTable miniTable = getMiniTable();

		final PackingMd model = getModel();
		final Set<TableRowKey> selectedTableRowKeys = model.getSelectedTableRowKeys();

		int rowIdx = 0;
		final boolean loadingOld = miniTable.setLoading(true);
		try
		{
			final List<TableRow> tableRows = model.getAggregatedTableRows();

			miniTable.setRowCount(tableRows.size());
			for (final TableRow currentRow : tableRows)
			{
				updateMiniTableRow(miniTable, rowIdx, currentRow);

				// prepare next
				rowIdx++;
			}
			miniTable.setRowCount(rowIdx);
			model.setSelectedTableRowKeys(selectedTableRowKeys);
			miniTable.autoSize();
		}
		finally
		{
			miniTable.setLoading(loadingOld);
		}
	}

	protected final void updateMiniTableRow(final IMiniTable miniTable, final int rowIdx, final TableRow currentRow)
	{
		final TableRowKey key = currentRow.getKey();

		// set values
		setValueAt(miniTable, COLUMNNAME_ROWID, rowIdx, new IDColumn(key.hashCode())); // ID
		setValueAt(miniTable, COLUMNNAME_M_Product_ID, rowIdx, currentRow.getProductName());
		setValueAt(miniTable, COLUMNNAME_Qty, rowIdx, currentRow.getQtyToDeliver());
		setValueAt(miniTable, COLUMNNAME_DeliveryDate, rowIdx, currentRow.getDeliveryDate());
		setValueAt(miniTable, COLUMNNAME_PreparationDate, rowIdx, currentRow.getPreparationDate());
		setValueAt(miniTable, COLUMNNAME_BPValue, rowIdx, currentRow.getBpartnerValue()); // BP Value
		setValueAt(miniTable, COLUMNNAME_C_BPartner_Location_ID, rowIdx, currentRow.getBpartnerLocationName()); // BP Location Name
		setValueAt(miniTable, COLUMNNAME_M_Warehouse_Dest_ID, rowIdx, currentRow.getWarehouseDestName());

		final String matchingType;
		final PackingMd model = getModel();
		final ITableRowSearchSelectionMatcher matcher = model.getTableRowSearchSelectionMatcher();
		if (matcher != null && matcher.match(key))
		{
			matchingType = matcher.getName();
		}
		else
		{
			matchingType = null;
		}
		setValueAt(miniTable, COLUMNNAME_MatchingType, rowIdx, matchingType);
	}

	private final Map<String, Integer> columnName2index = new HashMap<>();

	private int addColumn(final IMiniTable miniTable, final String columnName)
	{
		miniTable.addColumn(columnName);

		final int columnIndex = miniTable.getColumnCount() - 1;
		columnName2index.put(columnName, columnIndex);

		return columnIndex;
	}

	private void setupColumnIfExists(final IMiniTable miniTable,
			final String columnName,
			final int displayType,
			final Class<?> columnClass)
	{
		final Integer columnIndex = columnName2index.get(columnName);
		if (columnIndex == null)
		{
			// column does not exist, skip it
			return;
		}

		final String columnNameTrl;
		if (IDColumn.class.equals(columnClass))
		{
			columnNameTrl = " ";
		}
		else if (Services.get(IDeveloperModeBL.class).isEnabled())
		{
			columnNameTrl = columnName;
		}
		else if (columnName.length() <= 2)
		{
			// don't translate short column names
			columnNameTrl = columnName;
		}
		else
		{
			columnNameTrl = Services.get(IMsgBL.class).translate(Env.getCtx(), columnName);
		}

		final boolean readonly = true;
		miniTable.setColumnClass(columnIndex, displayType, columnClass, readonly, columnNameTrl);
	}

	protected void setSelectionModel(final IMiniTable miniTable)
	{
		// nothing to do
	}

	@Override
	public void configureMiniTable(final IMiniTable miniTable)
	{
		columnName2index.clear();
		miniTable.setMultiSelection(false);
		setSelectionModel(miniTable);
		//
		// Create Columns
		addColumn(miniTable, COLUMNNAME_ROWID);

		addColumn(miniTable, COLUMNNAME_M_Product_ID);
		addColumn(miniTable, COLUMNNAME_Qty);
		addColumn(miniTable, COLUMNNAME_DeliveryDate); // 01676
		addColumn(miniTable, COLUMNNAME_PreparationDate); // 01676

		addColumn(miniTable, COLUMNNAME_MatchingType);

		//
		// Setup columns
		// NOTE: we need to do this in two phases (addColumns and then setup)
		// because there is a fucked up thing regarding org.compiere.minigrid.MiniTable.addColumn(String)
		// ... see the comment from there
		setupColumnIfExists(miniTable, COLUMNNAME_ROWID, DisplayType.ID, IDColumn.class);
		// ((MiniTable)miniTable).setColorColumn(idColumnIndex); // not needed
		setupColumnIfExists(miniTable, COLUMNNAME_M_Product_ID, DisplayType.String, String.class);
		setupColumnIfExists(miniTable, COLUMNNAME_Qty, DisplayType.Quantity, BigDecimal.class);
		setupColumnIfExists(miniTable, COLUMNNAME_DeliveryDate, DisplayType.Date, Timestamp.class); // 01676
		setupColumnIfExists(miniTable, COLUMNNAME_PreparationDate, DisplayType.Time, Timestamp.class);
		setupColumnIfExists(miniTable, COLUMNNAME_BPValue, DisplayType.String, String.class);
		setupColumnIfExists(miniTable, COLUMNNAME_C_BPartner_Location_ID, DisplayType.String, String.class);
		setupColumnIfExists(miniTable, COLUMNNAME_M_Warehouse_Dest_ID, DisplayType.String, String.class);
		setupColumnIfExists(miniTable, COLUMNNAME_MatchingType, DisplayType.String, String.class);

		miniTable.autoSize();
	}

	@Override
	public void initModel(MvcMdGenForm model)
	{
		// nothing to do
	}

	@Override
	public void setIsPos(boolean isPos)
	{
		final PackingMd model = getModel();
		model.setPOSMode(isPos);
	}

	@Override
	public void setSelection(Set<Integer> selection)
	{
		getModel().setSelection(selection);
	}

	@Override
	public Set<Integer> getSelectedScheduleIds(IMiniTable miniTable)
	{
		return getModel().getSelectedScheduleIds();
	}

	@Override
	public Set<KeyNamePair> getSelectedBPartners()
	{
		return getModel().getSelectedBPartners();
	}

	@Override
	public Set<LocalDate> getSelectedDeliveryDates()
	{
		return getModel().getSelectedDeliveryDates();
	}

	@Override
	public int getSelectedRow()
	{
		return getModel().getMiniTable().getSelectedRow();
	}

	@Override
	public String getLabelData()
	{
		final StringBuilder data = new StringBuilder();
		data.append("<html><font align='left'>");
		final List<IDColumn> ids = new ArrayList<>();
		final int[] rows = getSelectedRows();
		for (final int row : rows)
		{
			final IDColumn id = (IDColumn)getMiniTable().getValueAt(row, 0);
			ids.add(id);
		}

		final PackingMd model = getModel();
		final Collection<TableRow> selectedRows = new ArrayList<>();
		for (final IDColumn id : ids)
		{
			final int uniqueId = id.getRecord_ID();
			final Collection<TableRow> tableRowsForUniqueId = model.getTableRowsForUniqueId(uniqueId);
			selectedRows.addAll(tableRowsForUniqueId);
		}

		final HashMap<String, BigDecimal> products = new HashMap<>();
		for (final TableRow currentRow : selectedRows)
		{
			final String productName = currentRow.getProductName().trim();
			BigDecimal qty = products.get(productName);
			if (qty == null)
			{
				products.put(productName, currentRow.getQtyToDeliver());
			}
			else
			{
				qty = qty.add(currentRow.getQtyToDeliver());
				products.remove(productName);
				products.put(productName, qty);
			}
		}

		for (final Map.Entry<String, BigDecimal> entry : products.entrySet())
		{
			final String name = entry.getKey();
			BigDecimal totalQty = entry.getValue();
			//
			if (totalQty.scale() != 0)
			{
				totalQty = totalQty.setScale(2, BigDecimal.ROUND_HALF_UP);
			}

			data.append(totalQty)
					.append(" x ")
					.append(name);

			if (products.size() > 1)
			{
				data.append("<br>");
			}
		}

		data.append("</font></html>");
		return data.toString();
	}

	@Override
	public int[] getSelectedRows()
	{
		final IMiniTable miniTable = getModel().getMiniTable();
		return miniTable.getSelectedRows();
	}

	/**
	 *
	 * @return true if window was disposed or it's disposing
	 */
	@Override
	public boolean isDisposed()
	{
		if (disposed)
		{
			return true;
		}
		if (disposing)
		{
			return true;
		}

		return false;
	}

	@Override
	public void dispose()
	{
		if (disposing || disposed)
		{
			return;
		}
		disposing = true;
		try
		{
			if (waitIndicator != null)
			{
				waitIndicator.disposeInEDT();
				waitIndicator = null;
			}

			if (packageTerminal != null)
			{
				packageTerminal.dispose(); // this is not an IDisposable, so we need to deal with it here
				onPackageTerminalClosed();
				packageTerminal = null;
			}
		}
		finally
		{
			disposing = false;
			disposed = true;
		}
		// pickingPanel.dispose(); // this is called above
		// pickingTerminalPanel.dispose(); // this is the callee
	}

	private boolean disposing = false;
	private boolean disposed = false;

	@Override
	public void logout()
	{
		final SwingPickingTerminalPanel pickingTerminalPanel = getPickingTerminalPanel();
		pickingTerminalPanel.logout();
	}
}
