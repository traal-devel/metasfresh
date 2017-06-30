package de.metas.handlingunits.trace.model.interceptor;

import java.util.List;

import org.adempiere.ad.modelvalidator.annotations.DocValidate;
import org.adempiere.ad.modelvalidator.annotations.Interceptor;
import org.adempiere.mmovement.api.IMovementDAO;
import org.adempiere.util.Services;
import org.compiere.model.I_M_MovementLine;
import org.compiere.model.ModelValidator;

import de.metas.handlingunits.trace.HUTraceEvent;
import de.metas.handlingunits.trace.HUTraceType;
import de.metas.handlingunits.trace.HUTraceEvent.HUTraceEventBuilder;
import de.metas.handlingunits.trace.HUTraceUtil;
import de.metas.interfaces.I_M_Movement;
import lombok.NonNull;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2017 metas GmbH
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
@Interceptor(I_M_Movement.class)
public class M_Movement
{

	@DocValidate(timings =
		{
				ModelValidator.TIMING_AFTER_CLOSE,
				ModelValidator.TIMING_AFTER_COMPLETE,
				ModelValidator.TIMING_AFTER_REACTIVATE,
				ModelValidator.TIMING_AFTER_REVERSEACCRUAL,
				ModelValidator.TIMING_AFTER_REVERSECORRECT,
				ModelValidator.TIMING_AFTER_UNCLOSE,
				ModelValidator.TIMING_AFTER_VOID
		}, afterCommit = true)
	public void addTraceEvent(@NonNull final I_M_Movement movement)
	{
		final HUTraceEventBuilder builder = HUTraceEvent.builder()
				.movementId(movement.getM_Movement_ID())
				.docTypeId(movement.getC_DocType_ID())
				.docStatus(movement.getDocStatus())
				.type(HUTraceType.MATERIAL_MOVEMENT);

		final List<I_M_MovementLine> movementLines = Services.get(IMovementDAO.class).retrieveLines(movement);
		HUTraceUtil.createAndAddEvents(builder, movementLines.stream());
	}
}
