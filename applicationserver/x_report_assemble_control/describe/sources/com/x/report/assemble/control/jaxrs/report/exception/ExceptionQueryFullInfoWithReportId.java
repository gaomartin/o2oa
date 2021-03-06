package com.x.report.assemble.control.jaxrs.report.exception;

import com.x.base.core.project.exception.PromptException;

public class ExceptionQueryFullInfoWithReportId extends PromptException {

	private static final long serialVersionUID = 1859164370743532895L;

	public ExceptionQueryFullInfoWithReportId( Throwable e, String id ) {
		super("系统根据汇报ID查询汇报详细信息内容时发生异常.reportId:" + id , e );
	}
}
