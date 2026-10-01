package com.tgaws.web;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tgaws.common.datascope.DataScopeCondition;

import java.util.Set;

/**
 * 架构守护测试（T-704，《6》6.3.6 落地点；CI 构建失败拦截违规）：
 *
 * <ol>
 *   <li><b>分层依赖</b>：business 不得依赖 web；web 不得直接依赖 mapper（一律走业务服务）；</li>
 *   <li><b>mapper 反向隔离</b>：mapper 不得依赖 manager（数据层不反向依赖业务层）；</li>
 *   <li><b>数据权限签名</b>：隧道域 Mapper（patrol/rpt）的 select* 查询方法必须声明
 *       DataScopeCondition 参数（漏用即构建失败）；白名单内为按主键/无隧道维度的查询。</li>
 * </ol>
 */
@AnalyzeClasses(packages = "com.tgaws")
public class ArchUnitGuardTest {

    // ---------- 1) 分层依赖 ----------

    @ArchTest
    static final ArchRule businessNeverDependsOnWeb = ArchRuleDefinition.noClasses()
            .that().resideInAPackage("com.tgaws.business..")
            .should().dependOnClassesThat().resideInAPackage("com.tgaws.web..")
            .because("业务层不得依赖 Web 层（分层单向：web → business → common）");

    @ArchTest
    static final ArchRule webNeverTouchesMappers = ArchRuleDefinition.noClasses()
            .that().resideInAPackage("com.tgaws.web..")
            .should().dependOnClassesThat().resideInAPackage("com.tgaws.business..mapper..")
            .because("Web 层不得直接依赖 Mapper，一律经业务服务（T-704 分层断言）");

    // ---------- 2) mapper 反向隔离 ----------

    @ArchTest
    static final ArchRule mappersNeverDependOnManagers = ArchRuleDefinition.noClasses()
            .that().resideInAPackage("com.tgaws.business..mapper..")
            .should().dependOnClassesThat().resideInAPackage("com.tgaws.business..manager..")
            .because("数据层不得反向依赖业务层（mapper 单向被 service 调用）");

    // ---------- 3) 数据权限签名（隧道域查询强制携带 DataScopeCondition） ----------

    @ArchTest
    static final ArchRule tunnelScopedSelectsCarryScope = ArchRuleDefinition.classes()
            .that().resideInAnyPackage("com.tgaws.business.patrol.mapper",
                    "com.tgaws.business.rpt.mapper")
            .should(new ArchCondition<JavaClass>("select* 查询方法必须声明 DataScopeCondition 参数") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    for (JavaMethod method : javaClass.getAllMethods()) {
                        if (!method.getName().startsWith("select")) {
                            continue;
                        }
                        boolean carriesScope = method.getRawParameterTypes().stream()
                                .anyMatch(t -> t.isEquivalentTo(DataScopeCondition.class));
                        boolean whitelisted = SCOPE_WHITELIST.contains(
                                javaClass.getSimpleName() + "." + method.getName());
                        if (!carriesScope && !whitelisted) {
                            events.add(SimpleConditionEvent.violated(method,
                                    "隧道域查询方法 " + javaClass.getSimpleName() + "."
                                            + method.getName()
                                            + " 缺少 DataScopeCondition 参数（漏用即越权，T-704 守护）"));
                        }
                    }
                }
            })
            .because("列表查询必须携带数据权限条件（scope=null 表示全部数据，由 Manager 层显式决策）");

    /**
     * 豁免白名单（逐条注明豁免理由）：
     * 单主键/无隧道维度查询与系统任务全量查询。
     */
    private static final Set<String> SCOPE_WHITELIST = Set.of(
            // 单主键查询（写/改/删路径的出口校验走 ScopeGuard，见《6》6.3.6）
            "PatrolMapper.selectTemplate",
            "PatrolMapper.selectTask",
            "PatrolMapper.selectPlan",
            "PatrolMapper.selectHazard",
            "PatrolMapper.selectRecordByClientKey",
            "PatrolMapper.selectRecordsByTask",
            "PatrolMapper.selectItemsByTemplate",
            "PatrolMapper.selectTemplates",        // 模板无隧道维度（全局配置）
            "PatrolMapper.selectEnabledPlans",     // 任务生成器系统任务（全量口径，无登录态）
            "RptStatDailyMapper.selectExistingDates", // 仅日期集合，无隧道数据泄漏
            "RptReportMapper.selectList",          // 报告记录全局台账（按生成记录查询）
            "RptReportMapper.selectById"           // 单主键查询（下载守卫走 B0802）
    );
}
